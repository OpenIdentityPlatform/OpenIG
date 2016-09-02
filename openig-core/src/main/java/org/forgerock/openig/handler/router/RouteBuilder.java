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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.forgerock.http.filter.Filters.newSessionFilter;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.json.resource.Resources.newHandler;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.openig.handler.router.MonitoringResourceProvider.DEFAULT_PERCENTILES;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.optionalHeapObject;
import static org.forgerock.openig.util.StringUtil.slug;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.audit.AuditService;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.routing.Router;
import org.forgerock.http.session.SessionManager;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.HttpAccessAuditFilter;
import org.forgerock.openig.filter.RuntimeExceptionFilter;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for new {@link Route}s.
 *
 * @since 2.2
 */
class RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RouteBuilder.class);

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
     * Builds a new route from the given configuration.
     *
     * @param config the JSON route configuration
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    Route build(final String routeId, final String routeName, final JsonValue config) throws HeapException {
        final Name routeHeapName = this.name.child(routeName);
        final HeapImpl routeHeap = new HeapImpl(heap, routeHeapName);

        final Router thisRouteRouter = new Router();
        thisRouteRouter.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);

        final Router objects = new Router();
        objects.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);

        final String slug = slug(routeId);
        // Preemptively get the endpoint path, without actually registering the routes/my-route router
        EndpointRegistry routeRegistry = new EndpointRegistry(thisRouteRouter, registry.pathInfo(slug));
        EndpointRegistry.Registration objectsReg = routeRegistry.register("objects", objects);

        routeHeap.put(ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(objects, objectsReg.getPath()));

        try {
            routeHeap.init(config.copy(), "handler", "session", "name", "condition", "auditService", "globalDecorators",
                           "monitor", "properties");

            Expression<Boolean> condition = config.get("condition").as(expression(Boolean.class));

            if (!slug.equals(routeId)) {
                logger.warn("Route name ('{}') has been transformed to a URL-friendly name ('{}') that is "
                                    + "exposed in endpoint URLs. To prevent this message, "
                                    + "consider renaming your route with the transformed name, "
                                    + "or provide your own appropriate value.",
                            routeName,
                            slug);
            }

            Handler routeHandler = setupRouteHandler(routeHeap, config, routeRegistry);
            return new Route(routeHandler, routeId, routeName, config, condition) {

                private EndpointRegistry.Registration registration;

                @Override
                public void start() {
                    // Register this route's endpoint into the parent registry
                    registration = registry.register(slug, thisRouteRouter);
                }

                @Override
                public void destroy() {
                    if (registration != null) {
                        registration.unregister();
                    }
                    routeHeap.destroy();
                }
            };
        } catch (HeapException | RuntimeException ex) {
            routeHeap.destroy();
            throw ex;
        }
    }

    private Handler setupRouteHandler(final HeapImpl routeHeap,
                                      final JsonValue config,
                                      final EndpointRegistry routeRegistry) throws HeapException {

        TimeService time = routeHeap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);

        List<Filter> filters = new ArrayList<>();

        SessionManager sessionManager = config.get("session").as(optionalHeapObject(routeHeap, SessionManager.class));
        if (sessionManager != null) {
            filters.add(newSessionFilter(sessionManager));
        }

        AuditService auditService = config.get("auditService").as(optionalHeapObject(routeHeap, AuditService.class));
        if (auditService != null && auditService.isRunning()) {
            filters.add(new HttpAccessAuditFilter(auditService, time));
        }

        MonitorConfig mc = getMonitorConfig(config.get("monitor"));
        if (mc.isEnabled()) {
            MonitoringMetrics metrics = new MonitoringMetrics();
            filters.add(new MetricsFilter(metrics));
            RequestHandler singleton = newHandler(new MonitoringResourceProvider(metrics, mc.getPercentiles()));
            EndpointRegistry.Registration monitoring = routeRegistry.register("monitoring", newHttpHandler(singleton));
            logger.info("Monitoring endpoint available at '{}'", monitoring.getPath());
        }

        // Ensure we always get a Response even in case of RuntimeException
        filters.add(new RuntimeExceptionFilter());

        // Log a message if the response is null
        filters.add(new NullResponseFilter());

        return chainOf(routeHeap.getHandler(), filters);
    }

    /**
     * Extract monitoring information from JSON.
     *
     * <p>Accepted formats:
     *
     * <pre>
     *     {@code
     *       "monitor": true
     *     }
     * </pre>
     *
     * <pre>
     *     {@code
     *       "monitor": {
     *           "enabled": false
     *       }
     *     }
     * </pre>
     *
     * <pre>
     *     {@code
     *       "monitor": {
     *           "enabled": "${true}",
     *           "percentiles": [ 0.1, 0.75, 0.99, 0.999 ]
     *       }
     *     }
     * </pre>
     *
     * By default (if omitted), monitoring is disabled.
     */
    private MonitorConfig getMonitorConfig(JsonValue monitor) {
        JsonValue evaluatedConfig = monitor.as(evaluated(heap.getProperties()));
        MonitorConfig mc = new MonitorConfig();
        if (evaluatedConfig.isMap()) {
            // enabled
            mc.setEnabled(evaluatedConfig.get("enabled").defaultTo(false).asBoolean());
            // percentiles
            mc.setPercentiles(evaluatedConfig.get("percentiles").defaultTo(DEFAULT_PERCENTILES).asList(Double.class));
        } else {
            // by default monitoring is disabled
            mc.setEnabled(evaluatedConfig.defaultTo(false).asBoolean());
        }
        return mc;
    }

    private static class MonitorConfig {
        private boolean enabled;
        private List<Double> percentiles = DEFAULT_PERCENTILES;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setPercentiles(List<Double> percentiles) {
            this.percentiles = percentiles;
        }

        public List<Double> getPercentiles() {
            return percentiles;
        }
    }
}
