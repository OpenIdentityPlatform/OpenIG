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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.log;

import static java.lang.String.format;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.openig.util.JsonValues.evaluated;

import java.io.PrintStream;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;

/**
 * A sink that writes log entries to the standard output or error stream (depending on the object configuration).
 */
public class ConsoleLogSink implements LogSink {

    private static final Object LOCK = new Object();

    /**
     * Where do the user want its log written to ?
     */
    enum Stream {
        OUT {
            @Override
            PrintStream getStream(final LogEntry entry) {
                return System.out;
            }
        },
        ERR {
            @Override
            PrintStream getStream(final LogEntry entry) {
                return System.err;
            }
        },
        AUTO {
            @Override
            PrintStream getStream(final LogEntry entry) {
                PrintStream stream = System.out;
                if (entry.getLevel().compareTo(LogLevel.INFO) > 0) {
                    stream = System.err;
                }
                return stream;
            }
        };

        /**
         * Returns the appropriate stream to write the entry to.
         */
        abstract PrintStream getStream(LogEntry entry);
    }

    /** The level of log entries to display in the console (default: {@link LogLevel#INFO INFO}). */
    private LogLevel level = LogLevel.INFO;

    /** Specify which PrintStream to use when printing a log statement. */
    private Stream stream = Stream.ERR;

    /**
     * Sets the level of log entries to display in the console.
     * @param level level of log entries to display in the console
     */
    public void setLevel(final LogLevel level) {
        this.level = level;
    }

    /**
     * Sets the stream to write entries to.
     * @param stream the stream to write entries to.
     */
    public void setStream(final Stream stream) {
        this.stream = stream;
    }

    @Override
    public void log(LogEntry entry) {
        if (isLoggable(entry.getSource(), entry.getLevel())) {
            synchronized (LOCK) {
                PrintStream stream = this.stream.getStream(entry);
                writeEntry(stream, entry);
                if ("throwable".equals(entry.getType()) && (entry.getData() instanceof Throwable)) {
                    Throwable throwable = (Throwable) entry.getData();
                    writeShortThrowable(stream, throwable);
                    if (level.compareTo(LogLevel.DEBUG) <= 0) {
                        writeStackTrace(stream, throwable);
                    }
                }
                writeSeparator(stream);
                stream.flush();
            }
        }
    }

    private void writeSeparator(final PrintStream stream) {
        for (int i = 0; i < 30; i++) {
            stream.print('-');
        }
        stream.println();
    }

    private void writeEntry(final PrintStream stream, final LogEntry entry) {
        writeHeader(stream, entry.getTime(), entry.getLevel(), entry.getSource());
        writeMessage(stream, entry.getMessage());
    }

    private void writeShortThrowable(final PrintStream stream, final Throwable throwable) {
        // Print each of the chained exception's messages (in order)
        Throwable current = throwable;
        while (current != null) {
            writeMessage(stream, format("[%25s] > %s",
                                        current.getClass().getSimpleName(),
                                        current.getLocalizedMessage()));
            current = current.getCause();
        }
    }

    private void writeStackTrace(final PrintStream stream, final Throwable throwable) {
        stream.println();
        throwable.printStackTrace(stream);
    }

    private void writeHeader(final PrintStream stream, final long time, final LogLevel level, final Name name) {
        // Example: "Sun Jul 20 16:17:00 EDT 1969 (INFO) "
        stream.printf("%Tc (%s) %s%n",
                      time,
                      level.name(),
                      name.getLeaf());
    }

    private void writeMessage(final PrintStream stream, final String message) {
        stream.println(message);
    }

    @Override
    public boolean isLoggable(Name source, LogLevel level) {
        return (level.compareTo(this.level) >= 0);
    }

    /**
     * Creates and initializes a console sink in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            JsonValue evaluated = config.as(evaluated());
            ConsoleLogSink sink = new ConsoleLogSink();
            sink.setLevel(evaluated.get("level")
                                   .defaultTo(sink.level.name())
                                   .as(enumConstant(LogLevel.class)));
            sink.setStream(evaluated.get("stream")
                                    .defaultTo(sink.stream.name())
                                    .as(enumConstant(Stream.class)));
            return sink;
        }
    }
}
