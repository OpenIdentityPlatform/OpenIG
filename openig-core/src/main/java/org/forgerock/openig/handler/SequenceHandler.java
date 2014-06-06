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

package org.forgerock.openig.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Processes an exchange through a sequence of handlers. This allows multi-request processing
 * such as retrieving a form, extracting form content (e.g. nonce) and submitting in a
 * subsequent request.
 */
public class SequenceHandler extends GenericHandler {

    /** Binds sequenced handlers with sequence processing postconditions. */
    public static class Binding {
        /** Handler to dispatch exchange to. */
        Handler handler;
        /**
         * Postcondition evaluated to determine if sequence continues (default: {@code null} a.k.a. unconditional).
         */
        Expression postcondition;
    }

    /** Handlers and associated sequence processing postconditions. */
    public final List<Binding> bindings = new ArrayList<Binding>();

    /**
     * Handles an HTTP the exchange by processing it through a sequence of handlers.
     */
    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        for (Binding binding : bindings) {
            if (exchange.response != null && exchange.response.entity != null) {
                exchange.response.entity.close(); // important!
            }
            exchange.response = null; // avoid downstream filters/handlers inadvertently using response
            binding.handler.handle(exchange);
            if (binding.postcondition != null && !Boolean.TRUE.equals(binding.postcondition.eval(exchange))) {
                break;
            }
        }
        timer.stop();
    }

    /** Creates and initializes a sequence handler in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            SequenceHandler handler = new SequenceHandler();
            for (JsonValue jv : config.get("bindings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                Binding binding = new Binding();
                binding.handler = HeapUtil.getRequiredObject(heap, jv.get("handler"), Handler.class);
                binding.postcondition = JsonValueUtil.asExpression(jv.get("postcondition")); // optional
                handler.bindings.add(binding);
            }
            return handler;
        }
    }
}
