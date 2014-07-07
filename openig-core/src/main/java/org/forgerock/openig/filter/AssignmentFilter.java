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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Conditionally assigns values to expressions before and after the exchange is handled.
 */
public class AssignmentFilter extends GenericFilter {

    /** Defines assignment condition, target and value expressions. */
    private static final class Binding {
        /** Condition to evaluate to determine if assignment should occur, or {@code null} if assignment is
         * unconditional. */
        private Expression condition;
        /** Expression that yields the target object whose value is to be set. */
        private Expression target;
        /** Expression that yields the value to be set in the target. */
        private Expression value;

        private Binding(final Expression condition, final Expression target, final Expression value) {
            this.condition = condition;
            this.target = target;
            this.value = value;
        }
    }

    /** Assignment bindings to apply before the request is handled. */
    private final List<Binding> onRequest = new ArrayList<Binding>();

    /** Assignment bindings to apply after the request is handled. */
    private final List<Binding> onResponse = new ArrayList<Binding>();

    /**
     * Registers an unconditional (always executed) binding on the request flow. The value stored in the target will be
     * {@literal null}.
     *
     * @param target
     *         Expression that yields the target object whose value is to be set
     * @return this object for fluent usage
     */
    public AssignmentFilter addRequestBinding(final Expression target) {
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
    public AssignmentFilter addRequestBinding(final Expression target, final Expression value) {
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
    public AssignmentFilter addRequestBinding(final Expression condition,
                                              final Expression target,
                                              final Expression value) {
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
    public AssignmentFilter addResponseBinding(final Expression target) {
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
    public AssignmentFilter addResponseBinding(final Expression target, final Expression value) {
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
    public AssignmentFilter addResponseBinding(final Expression condition,
                                               final Expression target,
                                               final Expression value) {
        this.onResponse.add(new Binding(condition, target, value));
        return this;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        for (Binding binding : onRequest) {
            eval(binding, exchange);
        }
        next.handle(exchange);
        for (Binding binding : onResponse) {
            eval(binding, exchange);
        }
        timer.stop();
    }

    private void eval(Binding binding, Exchange exchange) {
        if (binding.condition == null || Boolean.TRUE.equals(binding.condition.eval(exchange))) {
            binding.target.set(exchange, binding.value != null ? binding.value.eval(exchange) : null);
        }
    }

    /** Creates and initializes an assignment filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
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
                Expression condition = JsonValueUtil.asExpression(binding.get("condition"));
                Expression target = JsonValueUtil.asExpression(binding.get("target").required());
                Expression value = JsonValueUtil.asExpression(binding.get("value"));

                filter.addRequestBinding(condition, target, value);
            }
        }

        private void addResponseBindings(final AssignmentFilter filter) {
            // optional
            JsonValue bindings = config.get("onResponse").expect(List.class);
            for (JsonValue binding : bindings) {
                Expression condition = JsonValueUtil.asExpression(binding.get("condition"));
                Expression target = JsonValueUtil.asExpression(binding.get("target").required());
                Expression value = JsonValueUtil.asExpression(binding.get("value"));

                filter.addResponseBinding(condition, target, value);
            }
        }
    }
}
