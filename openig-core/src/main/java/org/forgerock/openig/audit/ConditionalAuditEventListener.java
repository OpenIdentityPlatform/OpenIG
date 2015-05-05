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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.audit;

import static java.lang.Boolean.*;
import static org.forgerock.openig.audit.AuditSystem.*;
import static org.forgerock.openig.util.JsonValues.*;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * A ConditionalAuditEventListener is conditionally invoked {@link AuditEventListener}.
 * <p>
 * It delegates to a given {@link AuditEventListener} if the configured condition evaluates to {@code true}.
 * If the condition evaluates to anything else ({@code false}, {@code null}, ...), the result is considered
 * as a {@code false}, and the delegate listener will not be invoked.
 * <p>
 * This class is not intended to be sub-classed, although its associated {@link org.forgerock.openig.heap.Heaplet} is.
 */
public class ConditionalAuditEventListener implements AuditEventListener {

    private final AuditEventListener delegate;
    private final Expression<Boolean> condition;

    /**
     * Builds a new ConditionalAuditEventListener that will delegates to the given {@code delegate} under the given
     * {@code condition}.
     *
     * @param delegate
     *         conditionally invoked listener
     * @param condition
     *         condition to evaluate
     */
    public ConditionalAuditEventListener(final AuditEventListener delegate, final Expression<Boolean> condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public void onAuditEvent(final AuditEvent event) {
        // Only process selected events
        if (TRUE.equals(condition.eval(event))) {
            delegate.onAuditEvent(event);
        }
    }

    /**
     * Creates and initializes a ConditionalListenerHeaplet in a heap environment.
     * <p>
     * Here is an example of an extending heap object declaration:
     * <pre>
     *     {@code
     *     {
     *         "name": "...",
     *         "type": "MySubTypeExtendingConditionalListener",
     *         "config": {
     *             "condition": "${contains(tags, 'marker')}",
     *             "any other": "configuration attributes"
     *         }
     *     }
     *     }
     * </pre>
     * The {@literal condition} property declares the condition that needs to be evaluated to {@code true} in order
     * to forward the event notification to the real listener. It defaults to {@code ${true}} (will always invoke the
     * delegate).
     */
    public abstract static class ConditionalListenerHeaplet extends GenericHeaplet {

        private AuditSystem auditSystem;
        private ConditionalAuditEventListener conditional;

        @Override
        public Object create() throws HeapException {
            Expression<Boolean> condition = asExpression(config.get("condition").defaultTo("${true}"),
                    Boolean.class);
            auditSystem = heap.get(AUDIT_SYSTEM_HEAP_KEY, AuditSystem.class);
            AuditEventListener listener = createListener();
            conditional = new ConditionalAuditEventListener(listener, condition);
            auditSystem.registerListener(conditional);
            return listener;
        }

        /**
         * Creates a new {@link AuditEventListener} that will be invoked if condition yields.
         *
         * @return a new {@link AuditEventListener} that will be invoked if condition yields.
         * @throws HeapException
         *             if an exception occurred during creation of the heap object or any of its dependencies.
         * @throws org.forgerock.json.fluent.JsonValueException
         *             if the heaplet (or one of its dependencies) has a malformed configuration.
         */
        protected abstract AuditEventListener createListener() throws HeapException;

        @Override
        public void destroy() {
            super.destroy();
            auditSystem.unregisterListener(conditional);
        }
    }
}
