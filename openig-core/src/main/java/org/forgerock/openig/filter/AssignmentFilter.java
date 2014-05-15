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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;

// OpenIG Core
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
    public static class Binding {
        /** Condition to evaluate to determine if assignment should occur, or {@code null} if assignment is
         * unconditional. */
        public Expression condition;
        /** Expression that yields the target object whose value is to be set. */
        public Expression target;
        /** Expression that yields the value to be set in the target. */
        public Expression value;
    }

    /** Assignment bindings to apply before the request is handled. */
    public final List<Binding> onRequest = new ArrayList<Binding>();

    /** Assignment bindings to apply after the request is handled. */
    public final List<Binding> onResponse = new ArrayList<Binding>();

    /**
     * Filters the request and/or response of an exchange by conditionally assigning values
     * to expressions before and after the exchange is handled.
     */
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
            result.onRequest.addAll(asBindings("onRequest"));
            result.onResponse.addAll(asBindings("onResponse"));
            return result;
        }

        private ArrayList<Binding> asBindings(String name) {
            ArrayList<Binding> result = new ArrayList<Binding>();
            JsonValue bindings = config.get(name).expect(List.class); // optional
            for (JsonValue binding : bindings) {
                result.add(asBinding(binding.required().expect(Map.class)));
            }
            return result;
        }

        private Binding asBinding(JsonValue value) {
            Binding result = new Binding();
            result.condition = JsonValueUtil.asExpression(value.get("condition")); // optional
            result.target = JsonValueUtil.asExpression(value.get("target").required()); // required
            result.value = JsonValueUtil.asExpression(value.get("value")); // optional
            return result;
        }
    }
}
