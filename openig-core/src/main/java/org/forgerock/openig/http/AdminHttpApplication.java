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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.http.util.Paths.addLeadingSlash;
import static org.forgerock.json.resource.Resources.newHandler;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.openig.heap.Keys.API_PROTECTION_FILTER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.CAPTURE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TICKER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIMER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.http.RunMode.EVALUATION;
import static org.forgerock.openig.http.RunMode.PRODUCTION;
import static org.forgerock.openig.util.CrestUtil.newCrestApplication;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Ticker;
import org.forgerock.http.DescribedHttpApplication;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.http.swagger.OpenApiRequestFilter;
import org.forgerock.http.swagger.SwaggerApiProducer;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.EnvironmentHeap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.ui.record.RecordProvider;
import org.forgerock.openig.ui.record.RecordService;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.models.Info;
import io.swagger.models.Scheme;

/**
 * Configuration class for the OpenIG Administration.
 */
public class AdminHttpApplication implements DescribedHttpApplication {

    private static final String API_TITLE = "OpenIG";
    private final EndpointRegistry endpointRegistry;
    private final String adminPrefix;
    private final JsonValue config;
    private final Router openigRouter;
    private final Environment environment;
    private final RunMode mode;
    private HeapImpl heap;
    private Factory<Buffer> storage;

    /**
     * Construct a {@link AdminHttpApplication}.
     *
     * @param adminPrefix the prefix to use in the URL to access the admin endpoints
     * @param config the admin configuration
     * @param environment the OpenIG environment
     * @param mode OpenIG run mode
     * @throws IOException when initialization failed
     */
    public AdminHttpApplication(String adminPrefix, JsonValue config, Environment environment, RunMode mode)
            throws IOException {
        this.adminPrefix = adminPrefix;
        this.config = config;
        this.environment = environment;
        this.mode = mode;
        this.openigRouter = new Router();

        // Provide the base tree:
        // /api/system/objects
        Router apiRouter = new Router();
        Router systemRouter = new Router();
        Router systemObjectsRouter = new Router();
        addSubRouter(openigRouter, "api", apiRouter);
        addSubRouter(apiRouter, "system", systemRouter);
        // TODO Could be removed after OPENIG-425 has been implemented
        // this is just to mimic the fact that 'system' should be a Route within a RouterHandler
        addSubRouter(systemRouter, "objects", systemObjectsRouter);
        systemObjectsRouter.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);

        if (EVALUATION.equals(mode)) {
            // Expose a UI-only storage service
            File data = new File(environment.getBaseDirectory(), "data/ui/records");
            RecordProvider provider = new RecordProvider(new RecordService(data));
            Handler recordHandler = newHttpHandler(newCrestApplication(newHandler(provider),
                                                                       "frapi:openig:internal:ui:record"));
            systemObjectsRouter.addRoute(requestUriMatcher(STARTS_WITH, "ui/record"), recordHandler);
        }

        // Expose Server info service
        Handler infoHandler = newHttpHandler(newCrestApplication(newHandler(new ServerInfoSingletonProvider()),
                                                                 "frapi:openig:server-info"));
        apiRouter.addRoute(requestUriMatcher(EQUALS, "info"), infoHandler);

        this.endpointRegistry = new EndpointRegistry(systemObjectsRouter, "/" + adminPrefix + "/api/system/objects");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Handler start() throws HttpApplicationException {
        if (heap != null) {
            throw new HttpApplicationException("Admin already started.");
        }

        try {
            // Create and configure the heap
            heap = new EnvironmentHeap(Name.of("admin-module"), environment);

            // "Live" objects
            heap.put(ENVIRONMENT_HEAP_KEY, environment);
            heap.put(TIME_SERVICE_HEAP_KEY, TimeService.SYSTEM);
            heap.put(TICKER_HEAP_KEY, Ticker.systemTicker());

            // can be overridden in config
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
            heap.put(CAPTURE_HEAP_KEY, new CaptureDecorator(CAPTURE_HEAP_KEY, false, false));
            heap.put(TIMER_HEAP_KEY, new TimerDecorator(TIMER_HEAP_KEY));

            heap.init(config, "temporaryStorage", "prefix", "mode");

            // As all heaplets can specify their own storage,
            // the following line provide custom storage available.
            storage = config.get("temporaryStorage")
                            .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                            .as(requiredHeapObject(heap, Factory.class));

            // Honor protection if declared in the configuration
            List<Filter> filters = new ArrayList<>();
            Filter protector = heap.get(API_PROTECTION_FILTER_HEAP_KEY, Filter.class);
            if (protector == null && PRODUCTION.equals(mode)) {
                // Force protection of the OpenIG API "namespace" when in production mode
                protector = new LoopbackAddressOnlyFilter();
            }
            if (protector != null) {
                filters.add(protector);
            }

            filters.add(new OpenApiRequestFilter());
            return chainOf(openigRouter, filters);
        } catch (HeapException e) {
            throw new HttpApplicationException(e);
        }
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return storage;
    }

    @Override
    public void stop() {
        if (heap != null) {
            heap.destroy();
        }
    }

    /**
     * Returns the API endpoint registry.
     * @return the API endpoint registry
     */
    public EndpointRegistry getEndpointRegistry() {
        return endpointRegistry;

    }

    /**
     * Returns the router that represents the /openig namespace (or whatever path/prefix value that was configured).
     * @return the router that represents the /openig namespace
     */
    protected Router getOpenIGRouter() {
        return openigRouter;
    }

    private static void addSubRouter(final Router base, final String name, final Handler router) {
        base.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
        base.addRoute(requestUriMatcher(STARTS_WITH, name), router);
    }

    /**
     * Needed to enforce generation of CREST APIs.
     *
     * @return a swagger api producer.
     */
    public SwaggerApiProducer getApiProducer() {
        return new SwaggerApiProducer(new Info().title(API_TITLE),
                                      addLeadingSlash(adminPrefix),
                                      null,
                                      Collections.<Scheme>emptyList());
    }

    /**
     * Permits only local clients to access /openig endpoint.
     */
    private class LoopbackAddressOnlyFilter implements Filter {

        private final Logger logger = LoggerFactory.getLogger(LoopbackAddressOnlyFilter.class);

        @Override
        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                              final Request request,
                                                              final Handler next) {
            ClientContext client = context.asContext(ClientContext.class);
            String remoteAddr = client.getRemoteAddress();
            try {
                // Accept any address that is bound to loop-back
                InetAddress[] addresses = InetAddress.getAllByName(remoteAddr);
                for (InetAddress address : addresses) {
                    if (address.isLoopbackAddress()) {
                        return next.handle(context, request);
                    }
                }
            } catch (UnknownHostException e) {
                logger.trace("Cannot resolve host '{}' when accessing '/{}'", remoteAddr, adminPrefix, e);
            }
            return newResponsePromise(new Response(Status.FORBIDDEN));
        }
    }
}

