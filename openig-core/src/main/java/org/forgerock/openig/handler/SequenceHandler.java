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

import static org.forgerock.openig.util.JsonValues.*;
import static org.forgerock.util.Utils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;

/**
 * Processes an exchange through a sequence of handlers. This allows multi-request processing such as retrieving a form,
 * extracting form content (e.g. nonce) and submitting in a subsequent request.
 */
public class SequenceHandler extends GenericHandler {

    /** Handlers and associated sequence processing postconditions. */
    private final List<Binding> bindings = new ArrayList<Binding>();

    /**
     * Binds sequenced handlers with sequence processing postconditions.
     *
     * @param handler
     *            The name of the handler heap object to dispatch to if the associated condition yields true.
     * @param postcondition
     *            evaluated to determine if sequence continues (default: {@code null} a.k.a. unconditional)
     * @return The current dispatch handler.
     */
    public SequenceHandler addBinding(final Handler handler, final Expression<Boolean> postcondition) {
        bindings.add(new Binding(handler, postcondition));
        return this;
    }

    /** Binds sequenced handlers with sequence processing postconditions. */
    private static class Binding {

        private final Handler handler;

        private final Expression<Boolean> postcondition;

        /**
         * Default constructor.
         *
         * @param handler
         *            Handler to dispatch exchange to.
         * @param postcondition
         *            Postcondition evaluated to determine if sequence continues (default: {@code null} a.k.a.
         *            unconditional).
         */
        Binding(Handler handler, Expression<Boolean> postcondition) {
            this.handler = handler;
            this.postcondition = postcondition;
        }
    }

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        for (Binding binding : bindings) {
            // avoid downstream filters/handlers inadvertently using response
            closeSilently(exchange.response);
            exchange.response = null;
            binding.handler.handle(exchange);
            if (binding.postcondition != null && !Boolean.TRUE.equals(binding.postcondition.eval(exchange))) {
                break;
            }
        }
    }

    /** Creates and initializes a sequence handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final SequenceHandler sequenceHandler = new SequenceHandler();
            for (final JsonValue jv : config.get("bindings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                final Handler handler = heap.resolve(jv.get("handler"), Handler.class);
                final Expression<Boolean> postcondition = asExpression(jv.get("postcondition"), Boolean.class);
                sequenceHandler.addBinding(handler, postcondition);
            }
            return sequenceHandler;
        }
    }
}
