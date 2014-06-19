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
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.util.Utils.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * Captures request and response messages for further analysis.
 */
public class CaptureFilter extends GenericFilter {

    /**
     * Provides an abstraction to make PrintWriter plugable.
     */
    public static interface WriterProvider {
        /**
         * Returns a valid PrintWriter.
         * @return a valid PrintWriter.
         * @throws IOException when a writer cannot be produced.
         */
        PrintWriter getWriter() throws IOException;
    }

    /**
     * Provides a {@link java.io.PrintWriter} instance based on a {@link java.io.File}.
     */
    public static class FileWriterProvider implements WriterProvider {
        /** File where captured output should be written. */
        private final File file;

        /** Character set to encode captured output with (default: UTF-8). */
        private final Charset charset;

        private PrintWriter writer;

        /**
         * Construct a new {@code FileWriterProvider} using the given file as
         * destination. Calling this constructor is equivalent to calling
         * {@link #FileWriterProvider(java.io.File, java.nio.charset.Charset)}
         * with {@literal UTF-8} as {@link java.nio.charset.Charset}.
         * 
         * @param file
         *            specify where the output will be flushed.
         */
        public FileWriterProvider(final File file) {
            this(file, Charset.forName("UTF-8"));
        }

        /**
         * Construct a new {@code FileWriterProvider} using the given file as destination and the given Charset.
         * @param file specify where the output will be flushed.
         * @param charset specify the {@link java.nio.charset.Charset} to use.
         */
        public FileWriterProvider(final File file, final Charset charset) {
            this.file = file;
            this.charset = charset;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null || !file.exists()) {
                if (writer != null) {
                    // file was removed while open
                    closeSilently(writer);
                }
                writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
            }
            return writer;
        }
    }


    /** Set of common textual content with non-text content-types to capture. */
    private static final HashSet<String> TEXT_TYPES = new HashSet<String>(
            Arrays.asList("application/atom+xml", "application/javascript", "application/json",
                    "application/rss+xml", "application/xhtml+xml", "application/xml", "application/xml-dtd",
                    "application/x-www-form-urlencoded")
    ); // make all entries lower case

    private Expression condition = null;

    private boolean captureEntity = true;

    /** Used to assign each exchange a monotonically increasing number. */
    private AtomicLong sequence = new AtomicLong(0L);

    /** Used to write captured output to a target destination (file, ...). */
    private WriterProvider provider;

    /**
     * Assign the given provider.
     * @param provider provider to be used.
     */
    public void setWriterProvider(final WriterProvider provider) {
        this.provider = provider;
    }

    /**
     * Used to conditionally capture the exchange. If the given expression evaluates to {@literal true},
     * both the request and the response will be captured.
     * Notice that the condition is evaluated when the request flows in this filter.
     * @param condition expression that evaluates to a {@link java.lang.Boolean}
     */
    public synchronized void setCondition(final Expression condition) {
        this.condition = condition;
    }

    /**
     * If set to {@literal true}, the message's entity will be captured as part of the output.
     * Notice that only entities with a text-based {@literal Content-Type} will be captured.
     * @param captureEntity capture the entity if possible
     */
    public void setCaptureEntity(final boolean captureEntity) {
        this.captureEntity = captureEntity;
    }

    @Override
    public synchronized void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Object eval = (condition != null ? condition.eval(exchange) : Boolean.TRUE);
        boolean doCapture = (eval instanceof Boolean && (Boolean) eval);
        long id = 0;
        if (doCapture) {
            id = sequence.incrementAndGet();
            captureRequest(exchange.request, id);
        }
        next.handle(exchange);
        if (doCapture) {
            captureResponse(exchange.response, id);
        }
        timer.stop();
    }

    private void captureRequest(Request request, long id) throws IOException {
        PrintWriter writer = provider.getWriter();
        writer.println();
        writer.println("--- REQUEST " + id + " --->");
        writer.println();
        writer.println(request.method + " " + request.uri + " " + request.version);
        writeHeaders(writer, request);
        writeEntity(writer, request);
        writer.flush();
    }

    private void captureResponse(Response response, long id) throws IOException {
        PrintWriter writer = provider.getWriter();
        writer.println();
        writer.println("<--- RESPONSE " + id + " ---");
        writer.println();
        writer.println(response.version + " " + response.status + " " + response.reason);
        writeHeaders(writer, response);
        writeEntity(writer, response);
        writer.flush();
    }

    private void writeHeaders(final PrintWriter writer, Message message) throws IOException {
        for (String key : message.headers.keySet()) {
            for (String value : message.headers.get(key)) {
                writer.println(key + ": " + value);
            }
        }
    }

    private void writeEntity(final PrintWriter writer, Message message) throws IOException {
        ContentTypeHeader contentType = new ContentTypeHeader(message);
        if (message.entity == null || contentType.getType() == null) {
            return;
        }
        writer.println();
        if (!captureEntity) {
            // simply show presence of an entity
            writer.println("[entity]");
            return;
        }
        if (!isTextualContent(contentType)) {
            writer.println("[binary entity]");
            return;
        }
        try {
            Reader reader = HttpUtil.entityReader(message, true, null);
            try {
                Streamer.stream(reader, writer);
            } finally {
                closeSilently(reader);
            }
        } catch (UnsupportedEncodingException uee) {
            writer.println("[entity contains data in unsupported encoding]");
        } catch (UnsupportedCharsetException uce) {
            writer.println("[entity contains characters in unsupported character set]");
        } catch (IllegalCharsetNameException icne) {
            writer.println("[entity contains characters in illegal character set]");
        }
        // entity may not terminate with new line, so here it is
        writer.println();
    }

    /**
     * Decide if the given content-type is printable or not.
     * The entity represents a textual/printable content if:
     * <ul>
     *     <li>there is a charset associated to the content-type, we'll be able to print it correctly</li>
     *     <li>the content type is in the category 'text' or it is an accepted type</li>
     * </ul>
     *
     * @param contentType the message's content-type
     * @return {@literal true} if the content-type represents a textual content
     */
    private boolean isTextualContent(final ContentTypeHeader contentType) {
        String type = (contentType.getType() != null ? contentType.getType().toLowerCase() : null);
        return contentType.getCharset() != null
                // text or white-listed type
                || (type != null && (TEXT_TYPES.contains(type) || type.startsWith("text/")));
    }

    /** Creates and initializes a capture filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            CaptureFilter filter = new CaptureFilter();
            filter.setWriterProvider(buildFileProvider(config));
            filter.setCondition(JsonValueUtil.asExpression(config.get("condition")));
            JsonValue capture = config.get("captureEntity");
            filter.setCaptureEntity(capture.defaultTo(filter.captureEntity).asBoolean());
            return filter;
        }

        private WriterProvider buildFileProvider(final JsonValue config) {
            File file = config.get("file").required().asFile();
            Charset charset = config.get("charset").defaultTo("UTF-8").asCharset();
            return new FileWriterProvider(file, charset);
        }
    }
}
