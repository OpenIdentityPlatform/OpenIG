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

import static org.forgerock.resource.core.routing.RoutingMode.EQUALS;

import java.util.Collections;

import org.forgerock.resource.core.Context;
import org.forgerock.resource.core.ServerContext;

public final class RouteMatcher<H> {

    private final RouterContext context;
    private final String match;
    private final String remaining;
    private final UriTemplate template;
    private final H handler;

    // Constructor for default route.
    RouteMatcher(Context context, String remaining, H handler) {
        this.template = null;
        this.match = null;
        this.remaining = remaining;
        this.context = new RouterContext(context, "", Collections.<String, String> emptyMap());
        this.handler = handler;
    }

    // Constructor for matching template.
    RouteMatcher(UriTemplate template, String match, String remaining, RouterContext context, H handler) {
        this.template = template;
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

    public boolean isBetterMatchThan(RouteMatcher matcher) {
        if (matcher == null) {
            return true;
        } else if (!match.equals(matcher.match)) {
            // One template matched a greater proportion of the resource
            // name than the other. Use the template which matched the most.
            return match.length() > matcher.match.length();
        } else if (template.getMode() != matcher.template.getMode()) {
            // Prefer equality match over startsWith match.
            return template.getMode() == RoutingMode.EQUALS;
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
