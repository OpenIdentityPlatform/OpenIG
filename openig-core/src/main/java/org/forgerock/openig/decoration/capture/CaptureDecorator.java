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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.capture;

import static java.lang.String.*;
import static java.util.Arrays.asList;
import static org.forgerock.openig.util.Json.*;

import java.util.Set;
import java.util.TreeSet;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
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
 *       "type": "DebugDecorator",
 *       "config": {
 *           "captureEntity": false
 *       }
 *     }
 *     }
 * </pre>
 * The capture decorator can be configured to globally enable entity capture using the {@literal captureEntity}
 * boolean attribute (default to {@code false}).
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
 * Notice that the attribute name in the decorated object <b>has to be</b> the same than the decorator
 * heap object name ({@code capture} in our example).
 *
 * A default {@literal capture} decorator is automatically created when OpenIG starts. It can be overridden
 * in the configuration files if default values are not satisfying.
 */
public class CaptureDecorator implements Decorator {

    /**
     * Key to retrieve a {@link CaptureDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String CAPTURE_HEAP_KEY = "capture";

    private final LogSink sink;
    private final boolean captureEntity;

    /**
     * Builds a new {@code capture} decorator with the given sink (possibly {@code null})
     * printing (or not) the entity content.
     * If the {@code sink} is specified (not {@code null}), every message intercepted by this decorator will be
     * send to the provided sink.
     *
     * @param sink Log Sink for message capture (may be {@code null})
     * @param captureEntity does the decorator needs to capture entity or not ?
     */
    public CaptureDecorator(final LogSink sink, final boolean captureEntity) {
        this.sink = sink;
        this.captureEntity = captureEntity;
    }

    @Override
    public boolean accepts(final Class<?> type) {
        return Filter.class.isAssignableFrom(type) || Handler.class.isAssignableFrom(type);
    }

    @Override
    public Object decorate(final Object delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        Set<CapturePoint> points = getCapturePoints(decoratorConfig);
        if (!points.isEmpty()) {
            // Only intercept if needed
            if (delegate instanceof Handler) {
                return new CaptureHandler((Handler) delegate, buildMessageCapture(context), points);
            } else if (delegate instanceof Filter) {
                return new CaptureFilter((Filter) delegate, buildMessageCapture(context), points);
            }
        }
        return delegate;
    }

    private Set<CapturePoint> getCapturePoints(final JsonValue decoratorConfig) throws HeapException {
        Set<CapturePoint> modes = new TreeSet<CapturePoint>();
        if (decoratorConfig.isString()) {
            // Single value
            modes.add(decoratorConfig.asEnum(CapturePoint.class));
        } else if (decoratorConfig.isList()) {
            // Array values
            modes.addAll(decoratorConfig.asList(ofEnum(CapturePoint.class)));
        } else {
            throw new HeapException(format("JSON element at %s should either be a simple String or an array of String",
                                           decoratorConfig.getPointer()));
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
        LogSink sink = this.sink;
        if (sink == null) {
            // Use the sink of the decorated component
            Heap heap = context.getHeap();
            sink = heap.resolve(context.getConfig().get("logSink").defaultTo(LogSink.LOGSINK_HEAP_KEY), LogSink.class);
        }
        return new MessageCapture(new Logger(sink, format("Debug[%s]", context.getName())),
                                  captureEntity);
    }

    /**
     * Creates and initializes a DebugDecorator in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            LogSink sink = heap.resolve(config.get("logSink"), LogSink.class, true);
            boolean captureEntity = config.get("captureEntity").defaultTo(false).asBoolean();
            return new CaptureDecorator(sink, captureEntity);
        }
    }
}
