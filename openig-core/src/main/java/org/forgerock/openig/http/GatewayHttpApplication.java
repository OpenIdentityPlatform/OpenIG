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

import static java.lang.String.format;
import static org.forgerock.http.filter.Filters.newSessionFilter;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.http.util.Json.readJsonLenient;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.API_PROTECTION_FILTER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.AUDIT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.AUDIT_SYSTEM_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.BASEURI_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.CAPTURE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SESSION_FACTORY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIMER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.optionalHeapObject;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.filter.TransactionIdOutboundFilter;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.audit.decoration.AuditDecorator;
import org.forgerock.openig.audit.internal.ForwardingAuditSystem;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.filter.Chain;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.LogSinkLoggerFactory;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.TimeService;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for configuring the OpenIG Gateway.
 *
 * @since 3.1.0
 */
@SuppressWarnings("deprecation")
public final class GatewayHttpApplication implements HttpApplication {
    /**
     * {@link Logger} instance for the openig-war module.
     */
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(GatewayHttpApplication.class);

    private static final JsonValue DEFAULT_CLIENT_HANDLER =
                                        json(object(field("name", CLIENT_HANDLER_HEAP_KEY),
                                                    field("type", ClientHandler.class.getName())));

    private static final JsonValue FORGEROCK_CLIENT_HANDLER =
                                json(object(field("name", FORGEROCK_CLIENT_HANDLER_HEAP_KEY),
                                            field("type", Chain.class.getName()),
                                            field("config", object(
                                                   field("filters", array(TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY)),
                                                   field("handler", CLIENT_HANDLER_HEAP_KEY)))));

    private static final JsonValue DEFAULT_SCHEDULED_THREAD_POOL =
            json(object(field("name", SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY),
                        field("type", "ScheduledExecutorService")));

    private HeapImpl heap;
    private TemporaryStorage storage;
    private Environment environment;

    /**
     * Default constructor called by the HTTP Framework.
     */
    public GatewayHttpApplication() {
        this(new GatewayEnvironment());
    }

    /**
     * Constructor for tests.
     */
    GatewayHttpApplication(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public Handler start() throws HttpApplicationException {
        if (heap != null) {
            throw new HttpApplicationException("Gateway already started.");
        }

        try {
            // Load the configuration
            final URL configurationURL = selectConfigurationUrl();
            final JsonValue config = readJson(configurationURL);
            TimeService timeService = TimeService.SYSTEM;

            // Create and configure the heap
            heap = new HeapImpl(Name.of(configurationURL.toString()));

            // Provide the base tree:
            // /openig/api/system/objects
            Router openigRouter = new Router();
            Router apiRouter = new Router();
            Router systemRouter = new Router();
            Router systemObjectsRouter = new Router();
            addSubRouter(openigRouter, "api", apiRouter);
            addSubRouter(apiRouter, "system", systemRouter);
            // TODO Could be removed after OPENIG-425 has been implemented
            // this is just to mimic the fact that 'system' should be a Route within a RouterHandler
            addSubRouter(systemRouter, "objects", systemObjectsRouter);
            systemObjectsRouter.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
            heap.put(ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(systemObjectsRouter,
                                                                      "/openig/api/system/objects"));

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
            heap.put(TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY, new TransactionIdOutboundFilter());
            heap.addDefaultDeclaration(DEFAULT_CLIENT_HANDLER);
            heap.addDefaultDeclaration(FORGEROCK_CLIENT_HANDLER);
            heap.addDefaultDeclaration(DEFAULT_SCHEDULED_THREAD_POOL);
            heap.init(config, "logSink", "temporaryStorage", "handler", "handlerObject", "globalDecorators");

            // As all heaplets can specify their own storage and logger,
            // these two lines provide custom logger or storage available.
            LogSink logSink = config.get("logSink")
                                    .defaultTo(LOGSINK_HEAP_KEY)
                                    .as(optionalHeapObject(heap, LogSink.class));
            final Logger logger = new Logger(logSink, Name.of(GatewayHttpApplication.class));
            storage = config.get("temporaryStorage")
                            .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                            .as(requiredHeapObject(heap, TemporaryStorage.class));

            // Protect the /openig namespace
            Filter protector = heap.get(API_PROTECTION_FILTER_HEAP_KEY, Filter.class);
            if (protector == null) {
                protector = new LoopbackAddressOnlyFilter(logger);
            }
            Handler restricted = chainOf(openigRouter, protector);

            Router router = new Router();
            router.addRoute(requestUriMatcher(STARTS_WITH, "openig"), restricted);

            // Create the root handler.
            List<Filter> filters = new ArrayList<>();

            // Let the user override the container's session.
            final SessionManager sessionManager = heap.get(SESSION_FACTORY_HEAP_KEY, SessionManager.class);
            if (sessionManager != null) {
                filters.add(newSessionFilter(sessionManager));
            }

            // Create the root handler
            Handler rootHandler = chainOf(heap.getHandler(), filters);
            router.setDefaultRoute(rootHandler);

            // Configure SLF4J with the LogSink defined by the user
            ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (factory instanceof LogSinkLoggerFactory) {
                ((LogSinkLoggerFactory) factory).setLogSink(logSink);
            }
            return router;
        } catch (Exception e) {
            LOG.error("Failed to initialise Http Application", e);
            // Release resources
            stop();
            throw new HttpApplicationException("Unable to start OpenIG", e);
        }
    }

    private URL selectConfigurationUrl() throws MalformedURLException {
        LOG.info("OpenIG base directory : {}", environment.getBaseDirectory());

        final File configuration = new File(environment.getConfigDirectory(), "config.json");
        final URL configurationURL;
        if (configuration.canRead()) {
            LOG.info("Reading the configuration from {}", configuration.getAbsolutePath());
            configurationURL = configuration.toURI().toURL();
        } else {
            LOG.info("{} not readable, using OpenIG default configuration", configuration.getAbsolutePath());
            configurationURL = getClass().getResource("default-config.json");
        }
        return configurationURL;
    }

    private static void addSubRouter(final Router base, final String name, final Handler router) {
        base.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
        base.addRoute(requestUriMatcher(STARTS_WITH, name), router);
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

    private static JsonValue readJson(URL resource) throws IOException {
        try (InputStream in = resource.openStream()) {
            return new JsonValue(readJsonLenient(in));
        } catch (FileNotFoundException e) {
            throw new IOException(format("File %s does not exist", resource), e);
        }
    }

    /**
     * Permits only local clients to access /openig endpoint.
     */
    private static class LoopbackAddressOnlyFilter implements Filter {
        private final Logger logger;

        public LoopbackAddressOnlyFilter(final Logger logger) {
            this.logger = logger;
        }

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
                logger.trace(format("Cannot resolve host '%s' when accessing '/openig'", remoteAddr));
            }
            return newResponsePromise(new Response(Status.FORBIDDEN));
        }
    }
}
