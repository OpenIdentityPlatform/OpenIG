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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.file;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;

/**
 * A sink that writes log entries to a file.
 * @deprecated Will be replaced by SLF4J / Logback in OpenIG 5.0
 */
@Deprecated
public class FileLogSink implements LogSink {

    /** File where the entries will be written to. */
    private final File file;

    /** Character set to encode log output with (default: UTF-8). */
    private final Charset charset;

    /** The level of log entries to display in the file (default: {@link LogLevel#INFO INFO}). */
    private LogLevel level = LogLevel.INFO;

    /** Wraps the file output for writing entries. */
    private PrintStream stream;

    /** Wraps the previous stream, prefixing all lines with a hash ('#') to have them appear as comments. */
    private PrintStream comment;

    /**
     * Builds a new FileLogSink writing entries in the given log file.
     *
     * @param file
     *         output where entries will be written (default to UTF-8 Charset)
     */
    public FileLogSink(final File file) {
        this(file, UTF_8);
    }

    /**
     * Builds a new FileLogSink writing entries in the given log file using the specified {@link Charset}.
     *
     * @param file
     *         output where entries will be written (default to UTF-8 Charset)
     * @param charset
     *         Character set to encode log output with
     */
    public FileLogSink(final File file, final Charset charset) {
        this.file = file;
        this.charset = charset;
    }

    /**
     * Sets the level of log entries to display in the file.
     * @param level level of log entries to display in the file
     */
    public void setLevel(final LogLevel level) {
        this.level = level;
    }

    @Override
    public void log(LogEntry entry) {
        if (isLoggable(entry.getSource(), entry.getLevel())) {
            synchronized (this) {
                try {
                    if (!file.exists() || stream == null) {
                        closeSilently(stream, comment);
                        stream = new PrintStream(new FileOutputStream(file, true), true, charset.name());
                        comment = new HashPrefixPrintStream(stream);
                    }

                    // Example: "Sun Jul 20 16:17:00 EDT 1969 (INFO) Source - Message"
                    stream.printf("%Tc %s %s --- %s%n",
                                  entry.getTime(),
                                  entry.getLevel().name(),
                                  entry.getSource().getLeaf(),
                                  entry.getMessage());

                    // Print the exception data (if any) on the commenting stream
                    if ("throwable".equals(entry.getType()) && (entry.getData() instanceof Throwable)) {
                        ((Throwable) entry.getData()).printStackTrace(comment);
                    }
                } catch (IOException ioe) {
                    // not much else we can do
                    System.err.println(ioe.getMessage());
                }
            }
        }
    }

    @Override
    public boolean isLoggable(Name source, LogLevel level) {
        return (level.compareTo(this.level) >= 0);
    }

    /** Creates and initializes a file log sink in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            JsonValue evaluated = config.as(evaluated());
            File file = evaluated.get("file").required().as(file());
            try {
                // try opening file to ensure it's writable at config time
                FileOutputStream out = new FileOutputStream(file, true);
                out.close();
            } catch (IOException ioe) {
                throw new JsonValueException(config.get("file"), ioe);
            }
            FileLogSink sink = new FileLogSink(file);
            sink.setLevel(evaluated.get("level").defaultTo(sink.level.name()).as(enumConstant(LogLevel.class)));
            return sink;
        }
    }

    private static class HashPrefixPrintStream extends PrintStream {

        private static final String HASH = "# ";

        public HashPrefixPrintStream(final PrintStream delegate) {
            super(delegate);
        }

        @Override
        public void println() {
            super.print(HASH);
            super.println();
        }

        @Override
        public void println(final String x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final boolean x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final char x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final int x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final long x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final float x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final double x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final char[] x) {
            super.print(HASH);
            super.println(x);
        }

        @Override
        public void println(final Object x) {
            super.print(HASH);
            super.println(x);
        }
    }
}
