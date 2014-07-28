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

package org.forgerock.openig.handler.router;

import static org.forgerock.openig.heap.HeapUtil.*;
import static org.forgerock.openig.util.JsonValueUtil.*;

import java.io.IOException;
import java.net.URI;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.util.URIUtil;

/**
 * A {@link Route} represents a separated configuration file that is loaded from a {@link RouterHandler}. Each route
 * has its own {@link Heap} for scoping configuration objects. The route's heap inherits from the global heap (it is
 * possible to make reference to objects defined in the global scope from the route's heap).
 *
 * <pre>
 * {
 *   "heap": {
 *     "objects": [
 *       {
 *         "name": "LogSink",
 *         "type": "ConsoleLogSink",
 *         "config": {
 *           "level": "DEBUG"
 *         }
 *       },
 *       {
 *         "name": "ClientHandler",
 *         "type": "ClientHandler",
 *         "config": {
 *         }
 *       },
 *     ]
 *   },
 *   "handler": "ClientHandler",
 *   "condition": "${exchange.request.headers['X-Forward'] == '/endpoint'}",
 *   "baseURI": "http://www.example.com",
 *   "name": "my-route"
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
 *   <li>{@literal baseURI}: a string used to rebase the request URL.</li>
 *   <li>{@literal name}: a string used name this route (may be used in route ordering).</li>
 * </ul>
 *
 * @see RouterHandler
 * @since 2.2
 */
class Route extends GenericHandler {

    /**
     * Contains objects, filters and handlers instances that may be used in this route.
     */
    private final HeapImpl heap;

    /**
     * Main entry point of this route.
     */
    private final Handler handler;

    /**
     * If the expression evaluates to {@literal true} for a given {@link Exchange}, this route
     * will process the exchange. May be {@literal null} (semantically equivalent to "always {@literal true}").
     */
    private final Expression condition;

    /**
     * URI to rebase the incoming request URI onto (may be {@literal null}).
     */
    private final URI baseURI;

    /**
     * Route's name (may be inferred from the file's name).
     */
    private final String name;

    /**
     * Builds a new Route from the given configuration.
     *
     * @param heap Heap that holds the objects defined in the route file
     * @param config Json representation of the file's content
     * @param defaultName default name of the route if none is found in the configuration
     * @throws HeapException if the heap does not contains the required handler object
     *         (or one of it's transitive dependencies)
     */
    public Route(final HeapImpl heap, final JsonValue config, final String defaultName) throws HeapException {
        this(heap,
             getRequiredObject(heap, config.get("handler"), Handler.class),
             config.get("name").defaultTo(defaultName).asString(),
             asExpression(config.get("condition")),
             config.get("baseURI").asURI());
    }

    /**
     * Builds a new Route.
     *
     * @param heap heap containing the objects associated to this route.
     * @param handler main handler of the route.
     * @param name route's name
     * @param condition used to dispatch only a subset of Exchanges to this route.
     * @param baseURI URI to rebase the request URI onto (may be {@literal null})
     */
    public Route(final HeapImpl heap,
                 final Handler handler,
                 final String name,
                 final Expression condition,
                 final URI baseURI) {

        this.heap = heap;
        this.handler = handler;
        this.name = name;
        this.condition = condition;
        this.baseURI = baseURI;
    }

    /**
     * Returns the route name.
     * @return the route name.
     */
    public String getName() {
        return name;
    }

    /**
     * Evaluate if this route will accept the given {@link Exchange}.
     * @param exchange used to evaluate the condition against
     * @return {@literal true} if the exchange matches the condition of this route.
     */
    public boolean accept(final Exchange exchange) {
        return (condition == null) || Boolean.TRUE.equals(condition.eval(exchange));
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        // Rebase the request URI if required before delegating
        if (baseURI != null) {
            exchange.request.uri = URIUtil.rebase(exchange.request.uri, baseURI);
        }
        handler.handle(exchange);
    }

    /**
     * Cleanup the resources used by this route.
     */
    public void destroy() {
        heap.destroy();
    }
}
