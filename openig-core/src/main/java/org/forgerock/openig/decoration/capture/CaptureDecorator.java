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

package org.forgerock.openig.decoration.capture;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.decoration.helper.LazyReference.newReference;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
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
 * The capture decorator can decorates both {@link Filter} and {@link Handler} instances. It enables
 * the user to see the messages coming in and out of the decorated object.
 * <p>
 * Multiple input/output can be intercepted:
 * <ul>
 *     <li>{@link CapturePoint#ALL}: Prints all of the messages</li>
 *     <li>{@link CapturePoint#FILTERED_REQUEST}: Prints the outgoing request (Filter only)</li>
 *     <li>{@link CapturePoint#FILTERED_RESPONSE}: Prints the outgoing response</li>
 *     <li>{@link CapturePoint#REQUEST}: Prints incoming + outgoing request</li>
 *     <li>{@link CapturePoint#RESPONSE}: Prints incoming + outgoing response</li>
 * </ul>
 * Notice that camel case is not important: {@literal all} equals {@literal ALL} and even {@literal AlL}.
 *
 * It has to be declared inside of the heap objects section:
 * <pre>
 *     {@code
 *     {
 *       "name": "capture",
 *       "type": "CaptureDecorator",
 *       "config": {
 *           "captureEntity": false,
 *           "captureContext": false
 *       }
 *     }
 *     }
 * </pre>
 * The capture decorator can be configured to globally enable entity capture using the {@literal captureEntity}
 * boolean attribute (default to {@code false}).
 * To capture the context at the capture point as well, use the {@literal captureContext} boolean attribute
 * (default to {@code false}), Note that {@literal captureExchange} is deprecated.
 * The common {@literal logSink} attribute can be used to force message capture in a given sink. By default, messages
 * are sent to the heap object defined LogSink.
 * <p>
 * To decorate a component, just add the decorator declaration next to the {@code config} element:
 * <pre>
 *     {@code
 *     {
 *       "type": "...",
 *       "capture": [ "FILTERED_REQUEST", "RESPONSE" ],
 *       "config": { ... }
 *     }
 *     }
 * </pre>
 *
 * Notice that the attribute name in the decorated object <b>has to be</b> the same as the decorator
 * heap object name ({@code capture} in our example).
 *
 * A default {@literal capture} decorator is automatically created when OpenIG starts. It can be overridden
 * in the configuration files if default values are not satisfying.
 */
public class CaptureDecorator extends AbstractHandlerAndFilterDecorator {

    private final LazyReference<LogSink> reference;
    private final boolean captureEntity;
    private final boolean captureContext;

    /**
     * Builds a new {@code capture} decorator with the given sink reference (possibly {@code null})
     * printing (or not) the entity content.
     * If the {@code sink} is specified (not {@code null}), every message intercepted by this decorator will be
     * send to the provided sink.
     *
     *  @param reference
     *         Log Sink reference for message capture (may be {@code null})
     * @param captureEntity
     *         {@code true} if the decorator needs to capture the entity, {@code false} otherwise
     * @param captureContext
     *         {@code true} if the decorator needs to capture the context,
     *         {@code false} otherwise
     */
    public CaptureDecorator(final LazyReference<LogSink> reference,
                            final boolean captureEntity,
                            final boolean captureContext) {
        this.reference = reference;
        this.captureEntity = captureEntity;
        this.captureContext = captureContext;
    }

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        Set<CapturePoint> points = getCapturePoints(decoratorConfig, context.getHeap());
        if (!points.isEmpty()) {
            // Only intercept if needed
            return new CaptureFilter(delegate, buildMessageCapture(context), points);
        }
        return delegate;
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        Set<CapturePoint> points = getCapturePoints(decoratorConfig, context.getHeap());
        if (!points.isEmpty()) {
            // Only intercept if needed
            return new CaptureHandler(delegate, buildMessageCapture(context), points);
        }
        return delegate;
    }

    private Set<CapturePoint> getCapturePoints(final JsonValue decoratorConfig, final Heap heap) throws HeapException {
        Set<CapturePoint> modes = new TreeSet<>();
        if (decoratorConfig.isNull()) {
            throw new HeapException("Capture's decorator cannot be null");
        }
        if (decoratorConfig.isString()) {
            // Single value
            modes.add(decoratorConfig.as(evaluated(heap.getBindings())).as(enumConstant(CapturePoint.class)));
        } else if (decoratorConfig.isList()) {
            // Array values
            List<CapturePoint> capturePoints = decoratorConfig.as(evaluated(heap.getBindings()))
                                                              .as(listOf(enumConstant(CapturePoint.class)));
            if (capturePoints.contains(null)) {
                throw new HeapException("Capture's decorator cannot contain null value");
            }
            modes.addAll(capturePoints);
        } else {
            throw new HeapException(format("Invalid JSON configuration in '%s'. It should either be a simple String "
                                           + " or an array of String", decoratorConfig.toString()));
        }

        // Sanity check
        if (modes.contains(CapturePoint.ALL)) {
            // Replace ALL by its implicitly referenced values
            modes.addAll(asList(CapturePoint.values()));
            modes.remove(CapturePoint.ALL);
        }
        return modes;
    }

    /**
     * Builds a new MessageCapture dedicated for the heap object context.
     *
     * @param context
     *         Context of the heap object
     * @return a new MessageCapture dedicated for the heap object context.
     * @throws HeapException
     *         when no logSink can be resolved (very unlikely to happen).
     */
    private MessageCapture buildMessageCapture(final Context context) throws HeapException {
        LogSink sink = (reference == null) ? null : reference.get();
        if (sink == null) {
            // Use the sink of the decorated component
            Heap heap = context.getHeap();
            sink = context.getConfig()
                          .get("logSink")
                          .defaultTo(LOGSINK_HEAP_KEY)
                          .as(requiredHeapObject(heap, LogSink.class));
        }
        Name name = context.getName();
        return new MessageCapture(new Logger(sink, name.decorated("Capture")), captureEntity, captureContext);
    }

    /**
     * Creates and initializes a CaptureDecorator in a heap environment.
     */
    public static class Heaplet extends DecoratorHeaplet {
        @Override
        public Decorator create() throws HeapException {
            LazyReference<LogSink> reference = newReference(heap,
                                                            config.get("logSink"),
                                                            LogSink.class,
                                                            true);

            JsonValue evaluated = config.as(evaluated(heap.getBindings()));
            boolean captureEntity = evaluated.get("captureEntity").defaultTo(false).asBoolean();

            // captureExchange is deprecated
            boolean captureContext = false;
            if (evaluated.isDefined("captureExchange")) {
                captureContext = evaluated.get("captureExchange").asBoolean();
            }
            if (evaluated.isDefined("captureContext")) {
                captureContext = evaluated.get("captureContext").asBoolean();
            }
            return new CaptureDecorator(reference, captureEntity, captureContext);
        }
    }
}
