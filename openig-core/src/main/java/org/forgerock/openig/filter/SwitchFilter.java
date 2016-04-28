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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Conditionally diverts the request to another handler. Before and after the request is
 * handled, associated conditions are evaluated. If a condition evaluates to {@code true}, then
 * the processing flow is diverted to the associated handler. If no condition evaluates to
 * {@code true}, then the request flows normally through the filter.
 */
public class SwitchFilter extends GenericHeapObject implements Filter {

    /** Associates a condition with a handler to divert to if the condition yields {@code true}. */
    private static class Case {
        /** Condition to evaluate if request should be diverted to handler. */
        private final Expression<Boolean> condition;

        /** Handler to divert to if condition yields {@code true}. */
        private final Handler handler;

        /**
         * Build a switch case from a condition and the handler to execute if condition yields.
         * @param condition expression to evaluate
         * @param handler handler to be executed if the condition yields
         */
        public Case(final Expression<Boolean> condition, final Handler handler) {
            this.condition = condition;
            this.handler = handler;
        }
    }

    /** Switch cases to test before the request is handled. */
    private final List<Case> requestCases = new ArrayList<>();

    /** Switch cases to test after the request is handled. */
    private final List<Case> responseCases = new ArrayList<>();

    /**
     * Add a request switch case with a condition and the handler to execute if condition yields.
     * @param condition expression to evaluate
     * @param handler handler to be executed if the condition yields
     * @return this filter for fluent invocation.
     */
    public SwitchFilter addRequestCase(final Expression<Boolean> condition, final Handler handler) {
        requestCases.add(new Case(condition, handler));
        return this;
    }

    /**
     * Add a response switch case with a condition and the handler to execute if condition yields.
     * @param condition expression to evaluate
     * @param handler handler to be executed if the condition yields
     * @return this filter for fluent invocation.
     */
    public SwitchFilter addResponseCase(final Expression<Boolean> condition, final Handler handler) {
        responseCases.add(new Case(condition, handler));
        return this;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        // Switch on the request flow
        Promise<Response, NeverThrowsException> promise = doSwitch(bindings(context, request),
                                                                   context,
                                                                   request,
                                                                   requestCases);
        if (promise != null) {
            return promise;
        }
        // not intercepted on request
        // Invoke next filter in chain and try switching on the response flow
        return next.handle(context, request)
                .thenAsync(new AsyncFunction<Response, Response, NeverThrowsException>() {
                    @Override
                    public Promise<Response, NeverThrowsException> apply(final Response value) {
                        Promise<Response, NeverThrowsException> promise = doSwitch(bindings(context,
                                                                                            request,
                                                                                            value),
                                                                                   context,
                                                                                   request,
                                                                                   responseCases);
                        // not intercepted on response, just return the original response
                        if (promise == null) {
                            promise = Promises.newResultPromise(value);
                        }
                        return promise;
                    }
                });
    }

    private Promise<Response, NeverThrowsException> doSwitch(Bindings bindings,
                                                             Context context,
                                                             Request request,
                                                             List<Case> cases) {
        for (Case c : cases) {
            if (c.condition == null || Boolean.TRUE.equals(c.condition.eval(bindings))) {
                // switched flow
                return c.handler.handle(context, request);
            }
        }
        // no interception
        return null;
    }

    /**
     * Creates and initializes an expect filter in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            SwitchFilter result = new SwitchFilter();
            result.requestCases.addAll(asCases("onRequest"));
            result.responseCases.addAll(asCases("onResponse"));
            return result;
        }

        private List<Case> asCases(String name) throws HeapException {
            List<Case> result = new ArrayList<>();
            JsonValue cases = config.get(name).expect(List.class);
            for (JsonValue value : cases) {
                result.add(asCase(value.required().expect(Map.class)));
            }
            return result;
        }

        private Case asCase(JsonValue value) throws HeapException {
            return new Case(value.get("condition").as(expression(Boolean.class)),
                            value.get("handler").as(requiredHeapObject(heap, Handler.class)));
        }
    }
}
