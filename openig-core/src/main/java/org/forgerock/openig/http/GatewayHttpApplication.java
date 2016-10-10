/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.forgerock.http.filter.Filters.newSessionFilter;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.BASEURI_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.CAPTURE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SESSION_FACTORY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TICKER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIMER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.TransactionIdOutboundFilter;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.heap.EnvironmentHeap;
import org.forgerock.util.Factory;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for configuring the OpenIG Gateway.
 *
 * @since 3.1.0
 */
public final class GatewayHttpApplication implements HttpApplication {

    private static final Logger logger = LoggerFactory.getLogger(GatewayHttpApplication.class);

    private static final JsonValue DEFAULT_CLIENT_HANDLER =
                                        json(object(field("name", CLIENT_HANDLER_HEAP_KEY),
                                                    field("type", "ClientHandler")));

    private static final JsonValue FORGEROCK_CLIENT_HANDLER =
                                json(object(field("name", FORGEROCK_CLIENT_HANDLER_HEAP_KEY),
                                            field("type", "Chain"),
                                            field("config", object(
                                                   field("filters", array(TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY)),
                                                   field("handler", CLIENT_HANDLER_HEAP_KEY)))));

    private static final JsonValue DEFAULT_SCHEDULED_THREAD_POOL =
            json(object(field("name", SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY),
                        field("type", "ScheduledExecutorService")));

    private HeapImpl heap;
    private Factory<Buffer> storage;
    private Environment environment;
    private final JsonValue config;
    private final EndpointRegistry endpointRegistry;

    /**
     * Construct a {@link GatewayHttpApplication}.
     *
     * @param environment the environment to lookup for configuration
     * @param config the gateway configuration
     * @param endpointRegistry the endpoint registry to bind the API endpoints
     */
    public GatewayHttpApplication(final Environment environment,
                                  final JsonValue config,
                                  final EndpointRegistry endpointRegistry) {
        this.environment = environment;
        this.config = config;
        this.endpointRegistry = endpointRegistry;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Handler start() throws HttpApplicationException {
        if (heap != null) {
            throw new HttpApplicationException("Gateway already started.");
        }

        try {
            // Create and configure the heap
            heap = new EnvironmentHeap(Name.of("gateway"), environment);

            heap.put(ENDPOINT_REGISTRY_HEAP_KEY, endpointRegistry);

            // "Live" objects
            heap.put(ENVIRONMENT_HEAP_KEY, environment);
            heap.put(TIME_SERVICE_HEAP_KEY, TimeService.SYSTEM);
            heap.put(TICKER_HEAP_KEY, Ticker.systemTicker());

            // can be overridden in config
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
            heap.put(CAPTURE_HEAP_KEY, new CaptureDecorator(CAPTURE_HEAP_KEY, false, false));
            heap.put(TIMER_HEAP_KEY, new TimerDecorator(TIMER_HEAP_KEY));
            heap.put(BASEURI_HEAP_KEY, new BaseUriDecorator(BASEURI_HEAP_KEY));
            heap.put(TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY, new TransactionIdOutboundFilter());
            heap.addDefaultDeclaration(DEFAULT_CLIENT_HANDLER);
            heap.addDefaultDeclaration(FORGEROCK_CLIENT_HANDLER);
            heap.addDefaultDeclaration(DEFAULT_SCHEDULED_THREAD_POOL);
            heap.init(config, "temporaryStorage", "handler", "handlerObject", "globalDecorators", "properties");

            storage = config.get("temporaryStorage")
                            .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                            .as(requiredHeapObject(heap, Factory.class));

            // Create the root handler.
            List<Filter> filters = new ArrayList<>();

            // Let the user override the container's session.
            final SessionManager sessionManager = heap.get(SESSION_FACTORY_HEAP_KEY, SessionManager.class);
            if (sessionManager != null) {
                filters.add(newSessionFilter(sessionManager));
            }

            // Create the root handler
            return chainOf(heap.getHandler(), filters);
        } catch (Exception e) {
            logger.error("Failed to initialise Http Application", e);
            // Release resources
            stop();
            throw new HttpApplicationException("Unable to start OpenIG", e);
        }
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return storage;
    }

    @Override
    public void stop() {
        if (heap != null) {
            // Try to release Heaplet(s) resources
            heap.destroy();
            heap = null;
        }
    }
}
