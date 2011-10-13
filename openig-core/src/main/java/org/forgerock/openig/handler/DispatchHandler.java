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

// Java Standard Edition
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonNode;
import org.forgerock.json.fluent.JsonNodeException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonNodeUtil;
import org.forgerock.openig.util.URIUtil;

/**
 * Dispatches to one of a list of handlers. When an exchange is handled, each handler's
 * condition is evalated. If a condition expression yields {@code true}, then the exchange
 * is dispatched to the associated handler with no further processing.
 * <p>
 * If no condition yields {@code true} then the handler will throw a {@link HandlerException}.
 * Therefore, it's advisable to have a single "default" handler at the end of the list
 * with no condition (unconditional) to handle otherwise undispatched requests. 
 *
 * @author Paul C. Bryan
 */
public class DispatchHandler extends GenericHandler {

    /** Binds an expression with a handler to dispatch to. */
    public static class Binding {

        /** Condition to dispatch to handler or {@code null} if unconditional. */
        public Expression condition;

        /** Handler to dispatch to. */
        public Handler handler;

        /** Overrides scheme/host/port of the request with a base URI. */
        public URI baseURI;
    }

    /** Expressions to evaluate against exchange, bound to handlers to dispatch to. */
    public final List<Binding> bindings = new ArrayList<Binding>();

    /**
     * Handles an HTTP the exchange by dispatching to a handler.
     */
    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        for (Binding binding : bindings) {
            if (binding.condition == null || Boolean.TRUE.equals(binding.condition.eval(exchange))) {
                if (binding.baseURI != null) {
                    try {
                        exchange.request.uri = URIUtil.rebase(exchange.request.uri, binding.baseURI);
                    } catch (URISyntaxException use) {
                        throw logger.debug(new HandlerException(use));
                    }
                }
                binding.handler.handle(exchange);
                timer.stop();
                return;
            }
        }
        throw logger.debug(new HandlerException("no handler to dispatch to"));
    }

    /**
     * Creates and initializes a dispatch handler in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonNodeException {
            DispatchHandler handler = new DispatchHandler();
            for (JsonNode node : config.get("bindings").expect(List.class)) {
                node.required().expect(Map.class);
                Binding binding = new Binding();
                binding.condition = JsonNodeUtil.asExpression(node.get("condition")); // default: unconditional
                binding.handler = HeapUtil.getRequiredObject(heap, node.get("handler"), Handler.class);
                binding.baseURI = node.get("baseURI").asURI(); // optional
                handler.bindings.add(binding);
            }
            return handler;
        }
    }
}
