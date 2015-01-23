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
 * {@code Handler} implementation of the {@link AbstractUriRouter} that will route
 * requests using URI template matching against the request's URI.
 *
 * @see AbstractUriRouter
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
        for (Context ctx = context; ctx != null; ctx = ctx.getParent()) {
            if (!ctx.containsContext(RouterContext.class)) {
                break;
            } else {
                matched.add(ctx.asContext(RouterContext.class).getMatchedUri());
            }
        }
        return new ResourceName(matched);
    }
}
