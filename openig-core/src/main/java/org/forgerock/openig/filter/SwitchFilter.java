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
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Conditionally diverts the exchange to another handler. Before and after the exchange is
 * handled, associated conditions are evaluated. If a condition evaluates to {@code true}, then
 * the exchange flow is diverted to the associated handler. If no condition evaluates to
 * {@code true}, then the exchange flows normally through the filter.
 */
public class SwitchFilter extends GenericFilter {

    /** Associates a condition with a handler to divert to if the condition yields {@code true}. */
    private static class Case {
        /** Condition to evaluate if exchange should be diverted to handler. */
        private final Expression condition;

        /** Handler to divert to if condition yields {@code true}. */
        private final Handler handler;

        /**
         * Build a switch case from a condition and the handler to execute if condition yields.
         * @param condition expression to evaluate
         * @param handler handler to be executed if the condition yields
         */
        public Case(final Expression condition, final Handler handler) {
            this.condition = condition;
            this.handler = handler;
        }
    }

    /** Switch cases to test before the exchange is handled. */
    private final List<Case> requestCases = new ArrayList<Case>();

    /** Switch cases to test after the exchange is handled. */
    private final List<Case> responseCases = new ArrayList<Case>();

    /**
     * Add a request switch case with a condition and the handler to execute if condition yields.
     * @param condition expression to evaluate
     * @param handler handler to be executed if the condition yields
     * @return this filter for fluent invocation.
     */
    public SwitchFilter addRequestCase(final Expression condition, final Handler handler) {
        requestCases.add(new Case(condition, handler));
        return this;
    }

    /**
     * Add a response switch case with a condition and the handler to execute if condition yields.
     * @param condition expression to evaluate
     * @param handler handler to be executed if the condition yields
     * @return this filter for fluent invocation.
     */
    public SwitchFilter addResponseCase(final Expression condition, final Handler handler) {
        responseCases.add(new Case(condition, handler));
        return this;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        if (!doSwitch(exchange, requestCases)) {
            // not intercepted
            next.handle(exchange);
            doSwitch(exchange, responseCases);
        }
        timer.stop();
    }

    private boolean doSwitch(Exchange exchange, List<Case> cases) throws HandlerException, IOException {
        for (Case c : cases) {
            Object o = (c.condition != null ? c.condition.eval(exchange) : Boolean.TRUE);
            if (o instanceof Boolean && ((Boolean) o)) {
                c.handler.handle(exchange);
                // switched flow
                return true;
            }
        }
        // no interception
        return false;
    }

    /**
     * Creates and initializes an expect filter in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            SwitchFilter result = new SwitchFilter();
            result.requestCases.addAll(asCases("onRequest"));
            result.responseCases.addAll(asCases("onResponse"));
            return result;
        }

        private List<Case> asCases(String name) throws HeapException {
            ArrayList<Case> result = new ArrayList<Case>();
            JsonValue cases = config.get(name).expect(List.class);
            for (JsonValue value : cases) {
                result.add(asCase(value.required().expect(Map.class)));
            }
            return result;
        }

        private Case asCase(JsonValue value) throws HeapException {
            return new Case(JsonValueUtil.asExpression(value.get("condition")),
                            HeapUtil.getRequiredObject(heap, value.get("handler"), Handler.class));
        }
    }
}
