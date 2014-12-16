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

    protected abstract T getThis();

    final Set<UriRoute<H>> getRoutes() {
        return routes;
    }

    public final T addAllRoutes(T router) {
        if (this != router) {
            routes.addAll(router.getRoutes());
        }
        return getThis();
    }

    public final UriRoute<H> addRoute(RoutingMode mode, String uriTemplate, H handler) {
        return addRoute(new UriRoute<H>(new UriTemplate<H>(mode, uriTemplate, handler)));
    }

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
     * Sets the request handler to be used as the default route for requests
     * which do not match any of the other defined routes.
     *
     * @param handler
     *            The request handler to be used as the default route.
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

    protected final RouteMatcher<H> getBestRoute(Context context, String uri) throws RouteNotFoundException {
        RouteMatcher<H> bestMatcher = null;
        for (UriRoute<H> route : routes) {
            RouteMatcher<H> matcher = route.getTemplate().getRouteMatcher(context, uri);
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
