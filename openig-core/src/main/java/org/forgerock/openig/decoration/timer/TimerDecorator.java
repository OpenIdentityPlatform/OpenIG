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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.forgerock.openig.heap.Keys.TICKER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.concurrent.TimeUnit;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.decoration.helper.AbstractHandlerAndFilterDecorator;
import org.forgerock.openig.decoration.helper.DecoratorHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.slf4j.LoggerFactory;

/**
 * The {@literal timer} decorator can decorate both {@link Filter} and
 * {@link Handler} instances. It will log elapsed time within the decorated heap
 * object.
 * <p>
 * It has to be declared inside of the heap objects section:
 *
 * <pre>
 * {@code
 * {
 *   "name": "myTimerDecorator",
 *   "type": "TimerDecorator"
 * }
 * }
 * </pre>
 * <p>
 * To decorate a component, just add the decorator declaration next to the
 * {@code config} element:
 * <pre>
 * {@code
 * {
 *   "type": "...",
 *   "timer": true,
 *   "config": { ... }
 * }
 * }
 * </pre>
 *
 * There is no special configuration required for this decorator. A default
 * {@literal timer} decorator is automatically created when OpenIG starts.
 */
public class TimerDecorator extends AbstractHandlerAndFilterDecorator {

    private final TimeUnit timeUnit;
    private final String name;

    /**
     * Builds a new {@code TimerDecorator} where the elapsed time unit is
     * milliseconds.
     *
     * @param name
     *            The name of this decorator.
     */
    public TimerDecorator(final String name) {
        this(name, MILLISECONDS);
    }

    /**
     * Builds a new {@code TimerDecorator} with the given {@code TimeUnit}
     * reference (not {@code null}), which logs the elapsed time within the
     * decorated heap object.
     *
     * @param name
     *            The name of this decorator.
     * @param timeUnit
     *            The {@code TimeUnit} of the elapsed time.
     */
    public TimerDecorator(final String name, final TimeUnit timeUnit) {
        this.name = name;
        this.timeUnit = checkNotNull(timeUnit, "The time unit must be set");
    }

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.as(evaluated(context.getHeap().getBindings())).asBoolean()) {
            return new TimerFilter(delegate,
                                   LoggerFactory.getLogger(getDecoratedObjectName(context)),
                                   lookupTicker(context.getHeap()),
                                   timeUnit);
        }
        return delegate;
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.as(evaluated(context.getHeap().getBindings())).asBoolean()) {
            return new TimerHandler(delegate,
                                    LoggerFactory.getLogger(getDecoratedObjectName(context)),
                                    lookupTicker(context.getHeap()),
                                    timeUnit);
        }
        return delegate;
    }

    private String getDecoratedObjectName(final Context context) {
        return context.getName().decorated(name).getLeaf();
    }

    private static Ticker lookupTicker(final Heap heap) throws HeapException {
        return heap.get(TICKER_HEAP_KEY, Ticker.class);
    }

    /**
     * Creates and initializes a TimerDecorator in a heap environment.
     */
    public static class Heaplet extends DecoratorHeaplet {
        @Override
        public Decorator create() throws HeapException {
            return new TimerDecorator(name.getLeaf());
        }
    }
}
