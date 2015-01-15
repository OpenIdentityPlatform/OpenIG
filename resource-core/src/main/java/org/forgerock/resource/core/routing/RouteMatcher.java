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

package org.forgerock.resource.core.routing;

import java.util.Collections;

import org.forgerock.resource.core.Context;
import org.forgerock.resource.core.ServerContext;

/**
 * An opaque handle for a route which has been registered in a {@link AbstractUriRouter
 * router}. A reference to a route should be maintained if there is a chance
 * that the route will need to be removed from the router at a later time.
 *
 * @see AbstractUriRouter
 *
 * @param <H> The type of the handler that will be used to handle routing requests.
 *
 * @since 1.0.0
 */
public final class RouteMatcher<H> {

    private final RouterContext context;
    private final String match;
    private final String remaining;
    private final UriRoute<H> route;
    private final H handler;

    // Constructor for default route.
    RouteMatcher(Context context, String remaining, H handler) {
        this.route = null;
        this.match = null;
        this.remaining = remaining;
        this.context = new RouterContext(context, "", Collections.<String, String> emptyMap());
        this.handler = handler;
    }

    // Constructor for matching template.
    RouteMatcher(UriRoute<H> route, String match, String remaining, RouterContext context, H handler) {
        this.route = route;
        this.match = match;
        this.remaining = remaining;
        this.context = context;
        this.handler = handler;
    }

    public String getRemaining() {
        return remaining;
    }

    public ServerContext getContext() {
        return context;
    }

    public H getHandler() {
        return handler;
    }

    boolean isBetterMatchThan(RouteMatcher matcher) {
        if (matcher == null) {
            return true;
        } else if (!match.equals(matcher.match)) {
            // One template matched a greater proportion of the resource
            // name than the other. Use the template which matched the most.
            return match.length() > matcher.match.length();
        } else if (route.getMode() != matcher.route.getMode()) {
            // Prefer equality match over startsWith match.
            return route.getMode() == RoutingMode.EQUALS;
        } else {
            // Prefer a match with less variables.
            return context.getUriTemplateVariables().size() < matcher.context
                    .getUriTemplateVariables().size();
        }
    }

    public boolean wasRouted() {
        return remaining != null;
    }
}
