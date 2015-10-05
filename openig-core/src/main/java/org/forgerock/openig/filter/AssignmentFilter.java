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

package org.forgerock.openig.filter;

import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;

import java.util.ArrayList;
import java.util.List;

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
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Conditionally assigns values to expressions before and after the exchange is handled.
 */
public class AssignmentFilter extends GenericHeapObject implements Filter {

    /** Defines assignment condition, target and value expressions. */
    private static final class Binding {
        /** Condition to evaluate to determine if assignment should occur, or {@code null} if assignment is
         * unconditional. */
        private Expression<Boolean> condition;
        /** Expression that yields the target object whose value is to be set. */
        private Expression<?> target;
        /** Expression that yields the value to be set in the target. */
        private Expression<?> value;

        private Binding(final Expression<Boolean> condition, final Expression<?> target, final Expression<?> value) {
            this.condition = condition;
            this.target = target;
            this.value = value;
        }
    }

    /** Assignment bindings to apply before the request is handled. */
    private final List<Binding> onRequest = new ArrayList<>();

    /** Assignment bindings to apply after the request is handled. */
    private final List<Binding> onResponse = new ArrayList<>();

    /**
     * Registers an unconditional (always executed) binding on the request flow. The value stored in the target will be
     * {@literal null}.
     *
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @return this object for fluent usage
     */
    public AssignmentFilter addRequestBinding(final Expression<?> target) {
        return this.addRequestBinding(target, null);
    }

    /**
     * Registers an unconditional (always executed) binding on the request flow. The value stored in the target will be
     * the result of the value {@link Expression}.
     *
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @param value
     *         Expression that yields the value to be set in the target (may be {@literal null})
     * @return this object for fluent usage
     */
    public AssignmentFilter addRequestBinding(final Expression<?> target, final Expression<?> value) {
        return this.addRequestBinding(null, target, value);
    }

    /**
     * Registers a conditional binding on the request flow. If the condition is fulfilled, the value stored in the
     * target will be the result of the value {@link Expression}.
     *
     * @param condition
     *         Condition to evaluate to determine if assignment should occur (may be {@literal null}, aka
     *         unconditional)
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @param value
     *         Expression that yields the value to be set in the target (may be {@literal null})
     * @return this object for fluent usage
     */
    public AssignmentFilter addRequestBinding(final Expression<Boolean> condition,
                                              final Expression<?> target,
                                              final Expression<?> value) {
        this.onRequest.add(new Binding(condition, target, value));
        return this;
    }

    /**
     * Registers an unconditional (always executed) binding on the response flow. The value stored in the target will be
     * {@literal null}.
     *
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @return this object for fluent usage
     */
    public AssignmentFilter addResponseBinding(final Expression<?> target) {
        return this.addResponseBinding(target, null);
    }

    /**
     * Registers an unconditional (always executed) binding on the response flow. The value stored in the target will be
     * the result of the value {@link Expression}.
     *
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @param value
     *         Expression that yields the value to be set in the target (may be {@literal null})
     * @return this object for fluent usage
     */
    public AssignmentFilter addResponseBinding(final Expression<?> target, final Expression<?> value) {
        return this.addResponseBinding(null, target, value);
    }

    /**
     * Registers a conditional binding on the response flow. If the condition is fulfilled, the value stored in the
     * target will be the result of the value {@link Expression}.
     *
     * @param condition
     *         Condition to evaluate to determine if assignment should occur (may be {@literal null}, aka
     *         unconditional)
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @param value
     *         Expression that yields the value to be set in the target (may be {@literal null})
     * @return this object for fluent usage
     */
    public AssignmentFilter addResponseBinding(final Expression<Boolean> condition,
                                               final Expression<?> target,
                                               final Expression<?> value) {
        this.onResponse.add(new Binding(condition, target, value));
        return this;
    }

    private void eval(Binding binding, final Bindings bindings) {
        if (binding.condition == null || Boolean.TRUE.equals(binding.condition.eval(bindings))) {
            binding.target.set(bindings, binding.value != null ? binding.value.eval(bindings) : null);
        }
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                       final Request request,
                                                       final Handler next) {
        final Exchange exchange = context.asContext(Exchange.class);

        for (Binding binding : onRequest) {
            eval(binding, bindings(exchange, request));
        }
        Promise<Response, NeverThrowsException> nextOne = next.handle(context, request);
        return nextOne.thenOnResult(new ResultHandler<Response>() {
            @Override
            public void handleResult(final Response result) {
                for (Binding binding : onResponse) {
                    eval(binding, bindings(exchange, request, result));
                }
            }
        });

    }

    /** Creates and initializes an assignment filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            AssignmentFilter result = new AssignmentFilter();
            addRequestBindings(result);
            addResponseBindings(result);
            return result;
        }

        private void addRequestBindings(final AssignmentFilter filter) {
            // optional
            JsonValue bindings = config.get("onRequest").expect(List.class);
            for (JsonValue binding : bindings) {
                Expression<Boolean> condition = asExpression(binding.get("condition"), Boolean.class);
                Expression<?> target = asExpression(binding.get("target").required(), Object.class);
                Expression<?> value = asExpression(binding.get("value"), Object.class);

                filter.addRequestBinding(condition, target, value);
            }
        }

        private void addResponseBindings(final AssignmentFilter filter) {
            // optional
            JsonValue bindings = config.get("onResponse").expect(List.class);
            for (JsonValue binding : bindings) {
                Expression<Boolean> condition = asExpression(binding.get("condition"), Boolean.class);
                Expression<?> target = asExpression(binding.get("target").required(), Object.class);
                Expression<?> value = asExpression(binding.get("value"), Object.class);

                filter.addResponseBinding(condition, target, value);
            }
        }
    }
}
