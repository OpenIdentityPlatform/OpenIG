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

package org.forgerock.openig.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.util.ISO8601;

/**
 * A sink that writes log entries to a file.
 */
public class FileLogSink implements LogSink {

    /**
     * Default {@link Charset} to use on the output file.
     */
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    /** File where the entries will be written to. */
    private final File file;

    /** Character set to encode log output with (default: UTF-8). */
    private final Charset charset;

    /** The level of log entries to display in the file (default: {@link LogLevel#INFO INFO}). */
    private LogLevel level = LogLevel.INFO;

    /** Wraps the file output for writing entries. */
    private PrintWriter writer;

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
        if (isLoggable(entry.source, entry.level)) {
            synchronized (this) {
                try {
                    if (!file.exists() || writer == null) {
                        if (writer != null) {
                            writer.close();
                        }
                        writer = new PrintWriter(new OutputStreamWriter(
                                new FileOutputStream(file, true), charset), true);
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(ISO8601.format(entry.time)).append(':').append(entry.source).append(':');
                    sb.append(entry.level).append(':').append(entry.message);
                    if (entry.data != null) {
                        sb.append(':').append(entry.data.toString());
                    }
                    writer.println(sb.toString());
                } catch (IOException ioe) {
                    // not much else we can do
                    System.err.println(ioe);
                }
            }
        }
    }

    @Override
    public boolean isLoggable(String source, LogLevel level) {
        return (level.compareTo(this.level) >= 0);
    }

    /** Creates and initializes a console sink in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            File file = config.get("file").required().asFile();
            try {
                // try opening file to ensure it's writable at config time
                FileOutputStream out = new FileOutputStream(file, true);
                out.close();
            } catch (IOException ioe) {
                throw new JsonValueException(config.get("file"), ioe);
            }
            FileLogSink sink = new FileLogSink(file);
            sink.setLevel(config.get("level").defaultTo(sink.level.toString()).asEnum(LogLevel.class));
            return sink;
        }
    }
}
