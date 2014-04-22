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
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
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
 *
 * @author Paul C. Bryan
 */
public class SwitchFilter extends GenericFilter {

    /** Associates a condition with a handler to divert to if the condition yields {@code true}. */
    public static class Case {
        /** Condition to evaluate if exchange should be diverted to handler. */
        public Expression condition;
        /** Handler to divert to if condition yields {@code true}. */
        public Handler handler;
    }

    /** Switch cases to test before the exchange is handled. */
    public final List<Case> onRequest = new ArrayList<Case>(); 

    /** Switch cases to test after the exchange is handled. */
    public final List<Case> onResponse = new ArrayList<Case>(); 

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        if (!doSwitch(exchange, onRequest)) { // not intercepted
            next.handle(exchange);
            doSwitch(exchange, onResponse);
        }
        timer.stop();
    }

    private boolean doSwitch(Exchange exchange, List<Case> cases) throws HandlerException, IOException {
        for (Case c : cases) {
            Object o = (c.condition != null ? c.condition.eval(exchange) : Boolean.TRUE);
            if (o instanceof Boolean && ((Boolean)o)) {
                c.handler.handle(exchange);
                return true; // switched flow
            }
        }
        return false; // no interception
    }

    /**
     * Creates and initializes an expect filter in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {
            SwitchFilter result = new SwitchFilter();
            result.onRequest.addAll(asCases("onRequest"));
            result.onResponse.addAll(asCases("onResponse"));
            return result;
        }
        private List<Case> asCases(String name) throws HeapException, JsonValueException {
            ArrayList<Case> result = new ArrayList<Case>();
            JsonValue cases = config.get(name).expect(List.class); // optional
            for (JsonValue _case : cases) {
                result.add(asCase(_case.required().expect(Map.class)));
            }
            return result;
        }
        private Case asCase(JsonValue _case) throws HeapException, JsonValueException {
            Case result = new Case();
            result.condition = JsonValueUtil.asExpression(_case.get("condition")); // optional
            result.handler = HeapUtil.getRequiredObject(heap, _case.get("handler"), Handler.class);
            return result;
        }
    }
}
