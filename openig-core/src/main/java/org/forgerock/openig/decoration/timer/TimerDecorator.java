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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.decoration.helper.AbstractHandlerAndFilterDecorator;
import org.forgerock.openig.decoration.helper.DecoratorHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;

/**
 * The {@literal timer} decorator can decorate both {@link Filter} and {@link Handler} instances.
 * It will log {@literal started}, {@literal elapsed} and {@literal elapsed-within} events into the {@link LogSink}
 * of the decorated heap object.
 * <p>
 * It has to be declared inside of the heap objects section:
 * <pre>
 *     {@code
 *     {
 *       "name": "timer",
 *       "type": "TimerDecorator"
 *     }
 *     }
 * </pre>
 * <p>
 * To decorate a component, just add the decorator declaration next to the {@code config} element:
 * <pre>
 *     {@code
 *     {
 *       "type": "...",
 *       "timer": true,
 *       "config": { ... }
 *     }
 *     }
 * </pre>
 *
 * There is no special configuration required for this decorator.
 *
 * A default {@literal timer} decorator is automatically created when OpenIG starts.
 */
public class TimerDecorator extends AbstractHandlerAndFilterDecorator {

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.as(evaluated(context.getHeap().getBindings())).asBoolean()) {
            return new TimerFilter(delegate, getLogger(context));
        }
        return delegate;
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.as(evaluated(context.getHeap().getBindings())).asBoolean()) {
            return new TimerHandler(delegate, getLogger(context));
        }
        return delegate;
    }

    /**
     * Builds a new Logger dedicated for the heap object context.
     *
     * @param context
     *         Context of the heap object
     * @return a new Logger dedicated for the heap object context.
     * @throws HeapException
     *         when no logSink can be resolved (very unlikely to happen).
     */
    private static Logger getLogger(final Context context) throws HeapException {
        // Use the sink of the decorated component
        Heap heap = context.getHeap();
        LogSink sink = context.getConfig()
                              .get("logSink")
                              .defaultTo(LOGSINK_HEAP_KEY)
                              .as(requiredHeapObject(heap, LogSink.class));
        Name name = context.getName();
        return new Logger(sink, name.decorated("Timer"));
    }

    /**
     * Creates and initializes a TimerDecorator in a heap environment.
     */
    public static class Heaplet extends DecoratorHeaplet {
        @Override
        public Decorator create() throws HeapException {
            return new TimerDecorator();
        }
    }
}
