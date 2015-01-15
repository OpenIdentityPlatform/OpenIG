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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.resource.core.routing;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.forgerock.resource.core.Context;

/**
 * A request handler which routes requests using URI template matching against
 * the request's resource name. Examples of valid URI templates include:
 *
 * <pre>
 * users
 * users/{userId}
 * users/{userId}/devices
 * users/{userId}/devices/{deviceId}
 * </pre>
 *
 * Routes may be added and removed from a router as follows:
 *
 * <pre>
 * RequestHandler users = ...;
 * Router router = new Router();
 * Route r1 = router.addRoute(EQUALS, &quot;users&quot;, users);
 * Route r2 = router.addRoute(EQUALS, &quot;users/{userId}&quot;, users);
 *
 * // Deregister a route.
 * router.removeRoute(r1, r2);
 * </pre>
 *
 * A request handler receiving a routed request may access the associated
 * route's URI template variables via
 * {@link org.forgerock.resource.core.routing.RouterContext#getUriTemplateVariables()}. For example, a request
 * handler processing requests for the route users/{userId} may obtain the value
 * of {@code userId} as follows:
 *
 * <pre>
 * String userId = context.asContext(RouterContext.class).getUriTemplateVariables().get(&quot;userId&quot;);
 * </pre>
 *
 * During routing resource names are "relativized" by removing the leading path
 * components which matched the template. See the documentation for
 * {@link org.forgerock.resource.core.routing.RouterContext} for more information.
 * <p>
 * <b>NOTE:</b> for simplicity this implementation only supports a small sub-set
 * of the functionality described in RFC 6570.
 *
 * @see RouterContext
 * @see <a href="http://tools.ietf.org/html/rfc6570">RFC 6570 - URI Template</a>
 *
 * @param <T> The type of the router.
 * @param <H> The type of the handler that will be used to handle routing requests.
 *
 * @since 1.0.0
 */
public abstract class AbstractUriRouter<T extends AbstractUriRouter<T, H>, H> {

    private volatile H defaultRoute = null;
    private final Set<UriRoute<H>> routes = new CopyOnWriteArraySet<UriRoute<H>>();

    /**
     * Creates a new router with no routes defined.
     */
    protected AbstractUriRouter() {
        // Nothing to do.
    }

    /**
     * Creates a new router containing the same routes and default route as the
     * provided router. Changes to the returned router's routing table will not
     * impact the provided router.
     *
     * @param router
     *            The router to be copied.
     */
    protected AbstractUriRouter(AbstractUriRouter<T, H> router) {
        this.defaultRoute = router.defaultRoute;
        this.routes.addAll(router.routes);
    }

    /**
     * Returns this {@code AbstractUriRouter} instance, typed correctly.
     *
     * @return This {@code AbstractUriRouter} instance.
     */
    protected abstract T getThis();

    /**
     * Gets all registered routes on this router.
     *
     * @return All registered routes.
     */
    final Set<UriRoute<H>> getRoutes() {
        return routes;
    }

    /**
     * Adds all of the routes defined in the provided router to this router. New
     * routes may be added while this router is processing requests.
     *
     * @param router
     *            The router whose routes are to be copied into this router.
     * @return This router.
     */
    public final T addAllRoutes(T router) {
        if (this != router) {
            routes.addAll(router.getRoutes());
        }
        return getThis();
    }

    /**
     * Adds a new route to this router for the provided request handler. New
     * routes may be added while this router is processing requests.
     *
     * @param mode
     *            Indicates how the URI template should be matched against
     *            resource names.
     * @param uriTemplate
     *            The URI template which request resource names must match.
     * @param handler
     *            The handler to which matching requests will be routed.
     * @return An opaque handle for the route which may be used for removing the
     *         route later.
     */
    public final UriRoute<H> addRoute(RoutingMode mode, String uriTemplate, H handler) {
        return addRoute(new UriRoute<H>(mode, uriTemplate, handler));
    }

    /**
     * Returns the handler to be used as the default route for requests
     * which do not match any of the other defined routes.
     *
     * @return The handler to be used as the default route.
     */
    public final H getDefaultRoute() {
        return defaultRoute;
    }

    /**
     * Removes all of the routes from this router. Routes may be removed while
     * this router is processing requests.
     *
     * @return This router.
     */
    public final T removeAllRoutes() {
        routes.clear();
        return getThis();
    }

    /**
     * Removes one or more routes from this router. Routes may be removed while
     * this router is processing requests.
     *
     * @param routes
     *            The routes to be removed.
     * @return {@code true} if at least one of the routes was found and removed.
     */
    public final boolean removeRoute(UriRoute<H>... routes) {
        boolean isModified = false;
        for (UriRoute route : routes) {
            isModified |= this.routes.remove(route);
        }
        return isModified;
    }

    /**
     * Sets the handler to be used as the default route for requests
     * which do not match any of the other defined routes.
     *
     * @param handler
     *            The handler to be used as the default route.
     * @return This router.
     */
    public final T setDefaultRoute(H handler) {
        this.defaultRoute = handler;
        return getThis();
    }

    private UriRoute<H> addRoute(UriRoute<H> route) {
        routes.add(route);
        return route;
    }

    /**
     * Finds the best route that matches the given {@code uri} based on the uri templates of the registered routes.
     * If no registered route matches at all then the default route is chosen, if present.
     *
     * @param context The request context.
     * @param uri The request uri to be matched against the registered routes.
     * @return A {@code RouteMatcher} containing the {@code UriRoute} which is the best match for the given {@code uri}.
     * @throws RouteNotFoundException If no route matched the given {@code uri}.
     */
    protected final RouteMatcher<H> getBestRoute(Context context, String uri) throws RouteNotFoundException {
        RouteMatcher<H> bestMatcher = null;
        for (UriRoute<H> route : routes) {
            RouteMatcher<H> matcher = route.getRouteMatcher(context, uri);
            if (matcher != null && matcher.isBetterMatchThan(bestMatcher)) {
                bestMatcher = matcher;
            }
        }
        if (bestMatcher != null) {
            return bestMatcher;
        }
        H handler = defaultRoute;

        /*
         * Passing the resourceName through explicitly means if an incorrect version was requested the error returned
         * is specific to the endpoint requested.
         */
        if (handler != null) {
            return new RouteMatcher<H>(context, uri, handler);
        }
        // TODO: i18n
        throw new RouteNotFoundException(String.format("Resource '%s' not found", uri));
    }
}
