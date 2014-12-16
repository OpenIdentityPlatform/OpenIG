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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.http.routing;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.http.NotFoundException;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.resource.core.routing.AbstractUriRouter;
import org.forgerock.resource.core.Context;
import org.forgerock.resource.core.ResourceName;
import org.forgerock.resource.core.routing.RouteMatcher;
import org.forgerock.resource.core.routing.RouteNotFoundException;
import org.forgerock.resource.core.routing.RouterContext;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

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
 * @see org.forgerock.resource.core.routing.RouterContext
 * @see UriRouter
 * @see <a href="http://tools.ietf.org/html/rfc6570">RFC 6570 - URI Template
 *      </a>
 */
public final class UriRouter extends AbstractUriRouter<UriRouter, Handler> implements Handler {

    /**
     * Creates a new router with no routes defined.
     */
    public UriRouter() {
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
    public UriRouter(UriRouter router) {
        super(router);
    }

    @Override
    protected UriRouter getThis() {
        return this;
    }

    @Override
    public Promise<Response, ResponseException> handle(Context context, Request request) throws ResponseException {
        try {
            RouteMatcher<Handler> bestMatch = getBestRoute(context, request);
            return bestMatch.getHandler().handle(bestMatch.getContext(), request);
        } catch (ResponseException e) {
            return Promises.newFailedPromise(e);
        }
    }

    private RouteMatcher<Handler> getBestRoute(Context context, Request request) throws ResponseException {

        ResourceName path = ResourceName.valueOf(request.getUri().getPath());
        if (context.containsContext(RouterContext.class)) {
            ResourceName matchedUri = getMatchedUri(context);
            path = path.tail(matchedUri.size());
        }
        String uri = path.toString();

        try {
            return getBestRoute(context, uri);
        } catch (RouteNotFoundException e) {
            throw new NotFoundException(e.getMessage(), e);
        }
    }

    private ResourceName getMatchedUri(Context context) {
        List<String> matched = new ArrayList<String>();
        for (Context ctx = context; ctx != null; ctx = context.getParent()) {
            if (!ctx.containsContext(RouterContext.class)) {
                break;
            } else {
                matched.add(context.asContext(RouterContext.class).getMatchedUri());
            }
        }
        return new ResourceName(matched);
    }
}
