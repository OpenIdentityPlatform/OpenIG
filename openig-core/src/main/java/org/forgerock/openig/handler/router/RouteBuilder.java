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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static java.lang.String.format;
import static org.forgerock.http.filter.Filters.newSessionFilter;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.http.util.Json.readJsonLenient;
import static org.forgerock.json.resource.Resources.newSingleton;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.JsonValues.evaluateJsonStaticExpression;
import static org.forgerock.openig.util.StringUtil.slug;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.audit.AuditService;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.routing.Router;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.HttpAccessAuditFilter;
import org.forgerock.openig.filter.RuntimeExceptionFilter;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.util.time.TimeService;

import com.codahale.metrics.MetricRegistry;

/**
 * Builder for new {@link Route}s.
 *
 * @since 2.2
 */
class RouteBuilder {

    /**
     * Heap to be used as parent for routes built from this builder.
     */
    private final HeapImpl heap;
    private final Name name;
    private final EndpointRegistry registry;

    /**
     * Builds a new builder.
     * @param heap parent heap for produced routes
     * @param name router name (used as parent name)
     * @param registry EndpointRegistry for supported routes
     */
    RouteBuilder(final HeapImpl heap, final Name name, final EndpointRegistry registry) {
        this.heap = heap;
        this.name = name;
        this.registry = registry;
    }

    /**
     * Builds a new route from the given resource file.
     *
     * @param resource route definition
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    Route build(final File resource) throws HeapException {
        final JsonValue config = readJson(resource);
        final Name routeHeapName = this.name.child(resource.getPath());
        final String defaultRouteName = withoutDotJson(resource.getName());

        return build(config, routeHeapName, defaultRouteName);
    }

    private static String withoutDotJson(final String path) {
        return path.substring(0, path.length() - ".json".length());
    }

    /**
     * Builds a new route from the given resource file.
     *
     * @param config route definition
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    Route build(final JsonValue config, final Name routeHeapName, final String defaultRouteName) throws HeapException {
        final HeapImpl routeHeap = new HeapImpl(heap, routeHeapName);
        String routeName = config.get("name").defaultTo(defaultRouteName).asString();

        Router thisRouteRouter = new Router();
        Router objects = new Router();
        objects.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
        thisRouteRouter.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
        thisRouteRouter.addRoute(requestUriMatcher(STARTS_WITH, "objects"), objects);
        String slug = slug(routeName);
        final EndpointRegistry.Registration registration = registry.register(slug, thisRouteRouter);
        routeHeap.put(ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(objects));

        try {
            routeHeap.init(config, "handler", "session", "name", "condition", "logSink", "audit-service",
                           "globalDecorators", "monitor");

            Expression<Boolean> condition = asExpression(config.get("condition"), Boolean.class);

            final LogSink logSink = routeHeap.resolve(config.get("logSink").defaultTo(LOGSINK_HEAP_KEY), LogSink.class);
            Logger logger = new Logger(logSink, routeHeapName);

            if (!slug.equals(routeName)) {
                logger.warning(format("Route name ('%s') has been converted to a slug ('%s') for URL exposition "
                                              + "(REST endpoints).",
                                      routeName,
                                      slug));
            }

            return new Route(setupRouteHandler(routeHeap, config, thisRouteRouter), routeName, condition) {
                @Override
                public void destroy() {
                    super.destroy();
                    registration.unregister();
                    routeHeap.destroy();
                }
            };
        } catch (HeapException | RuntimeException ex) {
            registration.unregister();
            routeHeap.destroy();
            throw ex;
        }
    }

    private Handler setupRouteHandler(final HeapImpl routeHeap,
                                      final JsonValue config,
                                      final Router routeRouter) throws HeapException {

        TimeService time = routeHeap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);

        List<Filter> filters = new ArrayList<>();

        SessionManager sessionManager = routeHeap.resolve(config.get("session"), SessionManager.class, true);
        if (sessionManager != null) {
            filters.add(newSessionFilter(sessionManager));
        }

        AuditService auditService = routeHeap.resolve(config.get("auditService"), AuditService.class, true);
        if (auditService != null && auditService.isRunning()) {
            filters.add(new HttpAccessAuditFilter(auditService, time));
        }

        if (evaluateJsonStaticExpression(config.get("monitor").defaultTo("${false}")).asBoolean()) {
            MetricRegistry registry = new MetricRegistry();
            filters.add(new MetricsFilter(registry));
            routeRouter.addRoute(requestUriMatcher(EQUALS, "monitoring"),
                                 newHttpHandler(newSingleton(new MetricRegistryResourceProvider(registry))));
        }

        // Ensure we always get a Response even in case of RuntimeException
        filters.add(new RuntimeExceptionFilter());

        return chainOf(routeHeap.getHandler(), filters);
    }

    /**
     * Reads the raw Json content from the route's definition file.
     *
     * @param resource
     *            The route definition file.
     * @return Json structure
     * @throws HeapException
     *             if there are IO or parsing errors
     */
    private JsonValue readJson(final File resource) throws HeapException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(resource);
            return new JsonValue(readJsonLenient(fis));
        } catch (FileNotFoundException e) {
            throw new HeapException(format("File %s does not exist", resource), e);
        } catch (IOException e) {
            throw new HeapException(format("Cannot read/parse content of %s", resource), e);
        } finally {
            closeSilently(fis);
        }
    }
}
