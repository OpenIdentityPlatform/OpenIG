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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.routing.Router;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.services.routing.RouteMatcher;

/**
 * Registry for OpenIG REST API endpoints.
 *
 * <p>Components can use that class to register additional endpoints into the {@literal /openig/api} namespace:
 *
 * <ul>
 *     <li>{@literal /openig/api/system/objects/[heap-object-name]} for components defined in {@code config.json}</li>
 *     <li>{@literal /openig/api/system/objects/.../[router-name]/routes/[route-name]/objects/[heap-object-name]} for
 *     components defined inside routes</li>
 * </ul>
 *
 * @see GenericHeaplet#endpointRegistry()
 */
public final class EndpointRegistry {
    private final Router router;

    /**
     * Creates a registry around the given Router instance.
     * Registered endpoints will be sub-elements of the given {@code router}.
     *
     * @param router base Router
     */
    public EndpointRegistry(final Router router) {
        this.router = router;
    }

    /**
     * Registers a new endpoint under the given {@code name}.
     *
     * <p>Equivalent to calling this {@code Router} code:
     * <pre>
     *     {@code
     *     router.addRoute(requestUriMatcher(STARTS_WITH, name), handler);
     *     }
     * </pre>
     *
     * @param name
     *         registered endpoint name
     * @param handler
     *         endpoint implementation
     * @return a handle for later endpoint un-registration
     */
    public Registration register(final String name, final Handler handler) {
        RouteMatcher<Request> matcher = requestUriMatcher(STARTS_WITH, name);
        router.addRoute(matcher, handler);
        return new Registration(router, matcher);
    }

    /**
     * Handle for un-registering an endpoint.
     */
    public static class Registration {
        private final Router router;
        private final RouteMatcher<Request> matcher;

        Registration(final Router router, final RouteMatcher<Request> matcher) {
            this.router = router;
            this.matcher = matcher;
        }

        /**
         * Un-register the endpoint.
         */
        public void unregister() {
            router.removeRoute(matcher);
        }
    }

}
