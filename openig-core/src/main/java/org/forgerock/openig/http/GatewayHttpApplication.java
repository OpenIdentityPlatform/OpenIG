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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static java.lang.String.*;
import static org.forgerock.http.Http.*;
import static org.forgerock.http.util.Json.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.heap.Keys.*;
import static org.forgerock.openig.http.HttpClient.*;
import static org.forgerock.util.Utils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.SessionManager;
import org.forgerock.http.io.Buffer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.audit.decoration.AuditDecorator;
import org.forgerock.openig.audit.internal.ForwardingAuditSystem;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.Factory;
import org.forgerock.util.time.TimeService;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for configuring the OpenIG Gateway.
 *
 * @since 3.1.0
 */
public final class GatewayHttpApplication implements HttpApplication {
    /**
     * {@link Logger} instance for the openig-war module.
     */
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(GatewayHttpApplication.class);

    /**
     * Key to retrieve the default {@link SessionManager} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    private static final String SESSION_FACTORY_HEAP_KEY = "Session";

    /**
     * Default HttpClient heap object declaration.
     */
    private static final JsonValue DEFAULT_HTTP_CLIENT = json(object(field("name", HTTP_CLIENT_HEAP_KEY),
                                                                     field("type", HttpClient.class.getName())));

    private HeapImpl heap;
    private TemporaryStorage storage;
    private volatile Handler httpHandler;

    /**
     * Default constructor called by the HTTP Framework.
     */
    public GatewayHttpApplication() {
        // Nothing to do.
    }

    @Override
    public Handler start() throws HttpApplicationException {
        if (httpHandler != null) {
            throw new HttpApplicationException("Gateway already started.");
        }

        try {
            // Load the configuration
            final Environment environment = new GatewayEnvironment();
            final File configuration = new File(environment.getConfigDirectory(), "config.json");
            final URL configurationURL = configuration.canRead() ? configuration.toURI().toURL() : getClass()
                    .getResource("default-config.json");
            final JsonValue config = readJson(configurationURL);
            TimeService timeService = TimeService.SYSTEM;

            // Create and configure the heap
            heap = new HeapImpl(Name.of(configurationURL.toString()));
            // "Live" objects
            heap.put(ENVIRONMENT_HEAP_KEY, environment);
            heap.put(TIME_SERVICE_HEAP_KEY, timeService);

            AuditSystem auditSystem = new ForwardingAuditSystem();

            // can be overridden in config
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
            heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
            heap.put(CAPTURE_HEAP_KEY, new CaptureDecorator(null, false, false));
            heap.put(TIMER_HEAP_KEY, new TimerDecorator());
            heap.put(AUDIT_HEAP_KEY, new AuditDecorator(auditSystem));
            heap.put(BASEURI_HEAP_KEY, new BaseUriDecorator());
            heap.put(AUDIT_SYSTEM_HEAP_KEY, auditSystem);
            heap.addDefaultDeclaration(DEFAULT_HTTP_CLIENT);
            heap.init(config, "logSink", "temporaryStorage", "handler", "handlerObject", "globalDecorators");

            // As all heaplets can specify their own storage and logger,
            // these two lines provide custom logger or storage available.
            final Logger logger = new Logger(heap.resolve(config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                    LogSink.class, true), Name.of("GatewayServlet"));
            storage = heap.resolve(config.get("temporaryStorage").defaultTo(TEMPORARY_STORAGE_HEAP_KEY),
                    TemporaryStorage.class);

            // Create the root handler.
            Handler rootHandler = new HttpHandler(heap.getHandler());

            // Let the user override the container's session.
            final SessionManager sessionManager =
                    heap.get(SESSION_FACTORY_HEAP_KEY, SessionManager.class);
            if (sessionManager != null) {
                rootHandler = chainOf(rootHandler, newSessionFilter(sessionManager));
            }

            httpHandler = rootHandler;
            return httpHandler;
        } catch (Exception e) {
            LOG.error("Failed to initialise Http Application", e);
            throw new HttpApplicationException("Unable to start OpenIG", e);
        }
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return storage;
    }

    @Override
    public void stop() {
        if (httpHandler != null) {
            heap.destroy();
            httpHandler = null;
        }
    }

    private static JsonValue readJson(URL resource) throws IOException {
        InputStream in = null;
        try {
            in = resource.openStream();
            return new JsonValue(readJsonLenient(in));
        } catch (FileNotFoundException e) {
            throw new IOException(format("File %s does not exists", resource), e);
        } finally {
            closeSilently(in);
        }
    }
}
