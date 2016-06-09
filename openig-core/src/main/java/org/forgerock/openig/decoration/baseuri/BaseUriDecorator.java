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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static org.forgerock.openig.decoration.helper.LazyReference.newReference;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.handler.Handlers;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.decoration.helper.AbstractHandlerAndFilterDecorator;
import org.forgerock.openig.decoration.helper.DecoratorHeaplet;
import org.forgerock.openig.decoration.helper.LazyReference;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;

/**
 * The {@literal baseURI} decorator can decorate both {@link Filter} and {@link Handler} instances.
 * <p>
 * It has to be declared inside of the heap objects section:
 * <pre>
 *     {@code
 *     {
 *       "name": "myBaseUri",
 *       "type": "BaseUriDecorator"
 *     }
 *     }
 * </pre>
 * To decorate a component, just add the decorator declaration next to the {@code config} element:
 * <pre>
 *     {@code
 *     {
 *       "type": "...",
 *       "myBaseUri": "http://www.example.com",
 *       "config": { ... }
 *     }
 *     }
 * </pre>
 * <p>
 * The {@literal baseURI} has to be a string otherwise, the decoration will be ignored.
 * <p>
 * N.B: The Gateway Servlet creates a default BaseUriDecorator named "baseURI" at startup time.
 */
public class BaseUriDecorator extends AbstractHandlerAndFilterDecorator {

    private final LazyReference<LogSink> reference;

    /**
     * Builds a new base uri decorator with a null sink reference.
     */
    public BaseUriDecorator() {
        this(null);
    }

    /**
     * Builds a new base uri decorator with the given sink reference (possibly
     * {@code null}).
     *
     * @param reference
     *            Log Sink reference for message capture (may be {@code null})
     */
    public BaseUriDecorator(final LazyReference<LogSink> reference) {
        this.reference = reference;
    }

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.isString()) {
            return Filters.chainOf(createBaseUriFilter(decoratorConfig, context), delegate);
        }
        return delegate;
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.isString()) {
            return Handlers.chainOf(delegate, createBaseUriFilter(decoratorConfig, context));
        }
        return delegate;
    }

    private BaseUriFilter createBaseUriFilter(JsonValue decoratorConfig, Context context) throws HeapException {
        return new BaseUriFilter(decoratorConfig.as(expression(String.class)), getLogger(context));
    }

    private Logger getLogger(final Context context) throws HeapException {
        LogSink sink = reference != null ? reference.get() : null;
        if (sink == null) {
            // Use the sink of the decorated component
            final Heap heap = context.getHeap();
            sink = context.getConfig()
                          .get("logSink")
                          .defaultTo(LOGSINK_HEAP_KEY)
                          .as(requiredHeapObject(heap, LogSink.class));
        }
        final Name name = context.getName();
        return new Logger(sink, name.decorated("BaseUri"));
    }

    /** Creates and initializes a baseUri in a heap environment. */
    public static class Heaplet extends DecoratorHeaplet {
        @Override
        public Decorator create() throws HeapException {
            final LazyReference<LogSink> reference = newReference(heap,
                                                                  config.get("logSink"),
                                                                  LogSink.class,
                                                                  true);
            return new BaseUriDecorator(reference);
        }
    }
}
