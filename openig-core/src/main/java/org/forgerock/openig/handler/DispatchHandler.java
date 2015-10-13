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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.forgerock.openig.util.JsonValues.asExpression;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Responses;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Dispatches to one of a list of handlers. When an exchange is handled, each handler's
 * condition is evaluated. If a condition expression yields {@code true}, then the exchange
 * is dispatched to the associated handler with no further processing.
 * <p>
 * If no condition yields {@code true} then the handler will return a {@literal 404} not found response.
 * Therefore, it's advisable to have a single "default" handler at the end of the list
 * with no condition (unconditional) to handle otherwise un-dispatched requests.
 */
public class DispatchHandler extends GenericHeapObject implements Handler {

    /** Expressions to evaluate against exchange, bound to handlers to dispatch to. */
    private final List<Binding> bindings = new ArrayList<>();

    /**
     * Binds an expression to the current handler to dispatch to.
     *
     * @param condition
     *            Condition to evaluate to determine if associated handler should be dispatched to. If omitted, then
     *            dispatch is unconditional.
     * @param handler
     *            The name of the handler heap object to dispatch to if the associated condition yields true.
     * @param baseURI
     *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host and
     *            port are used in the supplied URI. Default: leave URI untouched.
     * @return The current dispatch handler.
     */
    public DispatchHandler addBinding(Expression<Boolean> condition, Handler handler, URI baseURI) {
        bindings.add(new Binding(condition, handler, baseURI));
        return this;
    }

    /**
     * Adds an unconditional bindings to the handler.
     *
     * @param handler
     *            The name of the handler heap object to dispatch to if the associated condition yields true.
     * @param baseURI
     *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host and
     *            port are used in the supplied URI. Default: leave URI untouched.
     * @return The current dispatch handler.
     */
    public DispatchHandler addUnconditionalBinding(Handler handler, URI baseURI) {
        bindings.add(new Binding(null, handler, baseURI));
        return this;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Exchange exchange = context.asContext(Exchange.class);
        for (Binding binding : bindings) {
            if (binding.condition == null || Boolean.TRUE.equals(binding.condition.eval(exchange))) {
                if (binding.baseURI != null) {
                    request.getUri().rebase(binding.baseURI);
                }
                return binding.handler.handle(exchange, request);
            }
        }
        return Promises.newResultPromise(Responses.newNotFound("no handler to dispatch to"));
    }

    /** Binds an expression with a handler to dispatch to. */
    private static class Binding {

        /** Condition to dispatch to handler or {@code null} if unconditional. */
        private Expression<Boolean> condition;

        /** Handler to dispatch to. */
        private Handler handler;

        /** Overrides scheme/host/port of the request with a base URI. */
        private URI baseURI;

        /**
         * Constructor.
         *
         * @param condition
         *            Condition to evaluate to determine if associated handler should be dispatched to. If omitted, then
         *            dispatch is unconditional.
         * @param handler
         *            The name of the handler heap object to dispatch to if the associated condition yields true.
         * @param baseURI
         *            Overrides the existing request URI, making requests relative to a new base URI. Only scheme, host
         *            and port are used in the supplied URI. Default: leave URI untouched.
         */
        public Binding(Expression<Boolean> condition, Handler handler, URI baseURI) {
            super();
            this.condition = condition;
            this.handler = handler;
            this.baseURI = baseURI;
        }
    }

    /**
     * Creates and initializes a dispatch handler in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            DispatchHandler dispatchHandler = new DispatchHandler();
            for (JsonValue jv : config.get("bindings").expect(List.class)) {
                jv.required().expect(Map.class);
                final Expression<Boolean> expression = asExpression(jv.get("condition"), Boolean.class);
                final Handler handler = heap.resolve(jv.get("handler"), Handler.class);
                final URI uri = jv.get("baseURI").asURI();
                dispatchHandler.addBinding(expression, handler, uri);
            }
            return dispatchHandler;
        }
    }
}
