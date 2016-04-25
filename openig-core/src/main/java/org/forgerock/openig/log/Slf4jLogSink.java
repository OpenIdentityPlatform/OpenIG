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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.log;

import static org.forgerock.openig.util.StringUtil.slug;
import static org.forgerock.util.Reject.checkNotNull;
import static org.slf4j.MarkerFactory.getMarker;

import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * A sink that delegates log statements writing to SLF4J {@link org.slf4j.Logger}s.
 *
 * <p>It is useful when the user wants to unify its logs.
 *
 * <p>It gets a {@link org.slf4j.Logger} reference for each {@link LogEntry}, computing the actual
 * logger name with the composition of the {@code baseName} and the entry's {@linkplain LogEntry#getSource() source}
 * {@linkplain Name#getLeaf() leaf} value.
 *
 * <p>If an exception is found in the {@link LogEntry} attached {@linkplain LogEntry#getData() data},
 * the dedicated {@link org.slf4j.Logger} log method is used.
 *
 * <p>Usage:
 * <pre>
 *   {@code
 *   {
 *     "name": "LogSink",
 *     "type": "Slf4jLogSink",
 *     "config": {
 *       "base": "org.forgerock.openig"
 *     }
 *   }
 *   }
 * </pre>
 */
public class Slf4jLogSink implements LogSink {

    private final String baseName;
    private final ILoggerFactory factory;
    private final boolean useDot;

    Slf4jLogSink(String baseName, ILoggerFactory factory) {
        this.baseName = checkNotNull(baseName);
        this.factory = checkNotNull(factory);
        // By convention SLF4J loggers are using '.' as separator
        // Add one if it's missing from the base name
        this.useDot = !baseName.isEmpty() && !baseName.endsWith(".");
    }

    @Override
    public void log(LogEntry entry) {

        Marker marker = getMarker(entry.getType());
        org.slf4j.Logger logger = factory.getLogger(nameOf(entry.getSource()));
        switch (entry.getLevel()) {
        case TRACE:
            logger.trace(marker, entry.getMessage(), entry.getData());
            break;
        case DEBUG:
            logger.debug(marker, entry.getMessage(), entry.getData());
            break;
        case STAT:
        case CONFIG:
        case INFO:
            logger.info(marker, entry.getMessage(), entry.getData());
            break;
        case WARNING:
            logger.warn(marker, entry.getMessage(), entry.getData());
            break;
        case ERROR:
            logger.error(marker, entry.getMessage(), entry.getData());
            break;
        case ALL:
        case OFF:
            // no-op (it's not real log levels)
            break;
        }
    }

    private String nameOf(Name source) {
        StringBuilder sb = new StringBuilder(baseName);
        if (useDot) {
            sb.append('.');
        }
        // Logback, when reducing the length of the logger name, cut on '.'
        // That give strange results like: o.f.o.s./.json
        sb.append(slug(source.getLeaf()));
        return sb.toString();
    }

    @Override
    public boolean isLoggable(Name source, LogLevel level) {
        return true;
    }

    /**
     * Creates and initializes a SLF4J sink in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            return new Slf4jLogSink(
                    config.get("base").defaultTo("").asString(),
                    new ILoggerFactory() {
                        @Override
                        public org.slf4j.Logger getLogger(String name) {
                            return LoggerFactory.getLogger(name);
                        }
                    });
        }
    }
}
