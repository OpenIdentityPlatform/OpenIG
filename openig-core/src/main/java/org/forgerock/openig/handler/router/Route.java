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

import static org.forgerock.openig.el.Bindings.bindings;

import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.SessionManager;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.MDC;

/**
 * A {@link Route} represents a separated configuration file that is loaded from a {@link RouterHandler}. Each route has
 * its own {@link org.forgerock.openig.heap.Heap} for scoping configuration objects. The route's heap inherits from the
 * global heap (it is possible to make reference to objects defined in the global scope from the route's heap).
 *
 * <pre>
 * {@code
 * {
 *   "heap": [
 *     {
 *       "name": "MyJwtSession",
 *       "type": "JwtSession",
 *       "config": {
 *         ...
 *       }
 *     }
 *   ],
 *   "handler": "ClientHandler",
 *   "condition": "${request.headers['X-Forward'] == '/endpoint'}",
 *   "session": "MyJwtSession",
 *   "name": "my-route"
 * }
 * }
 * </pre>
 *
 * In addition of the {@literal heap} property, a route needs to define a reference to its main handler using the
 * {@literal handler} property (needs to point to a {@link Handler} object declared in the local or global heap).
 *
 * Extra properties are supported, but optional:
 * <ul>
 *   <li>{@literal condition}: an expression that will trigger the
 *       handler execution (if not defined, it always evaluate to true).</li>
 *   <li>{@literal name}: a string used name this route (may be used in route ordering).</li>
 *   <li>{@literal session}: the name of a declared heap object of type {@link SessionManager}.</li>
 * </ul>
 *
 * @see RouterHandler
 * @since 2.2
 */
abstract class Route implements Handler {

    /**
     * Main entry point of this route.
     */
    private final Handler handler;

    /**
     * If the expression evaluates to {@literal true} for a given {@link Request} and {@link Context}, this route
     * will process the request. May be {@literal null} (semantically equivalent to "always {@literal true}").
     */
    private final Expression<Boolean> condition;

    /**
     * Route's name (may be inferred from the file's name).
     */
    private final String name;

    /**
     * Builds a new Route.
     * @param handler main handler of the route.
     * @param name route's name
     * @param condition used to dispatch only a subset of incoming request to this route.
     */
    public Route(final Handler handler,
                 final String name,
                 final Expression<Boolean> condition) {
        this.handler = handler;
        this.name = name;
        this.condition = condition;
    }

    /**
     * Returns the route name.
     * @return the route name.
     */
    public String getName() {
        return name;
    }

    /**
     * Evaluate if this route will accept the given {@link Context} and {@link Request}.
     * @param context used to evaluate the condition against
     * @param request used to evaluate the condition against
     * @return {@literal true} if the provided context and request match the condition of this route.
     */
    public boolean accept(final Context context, Request request) {
        return (condition == null) || Boolean.TRUE.equals(condition.eval(bindings(context, request)));
    }

    /**
     * Hook this route into the system.
     */
    public abstract void start();

    /**
     * Cleanup the resources used by this route.
     */
    public abstract void destroy();

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("routeId", name);
            return handler.handle(context, request);
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }
}
