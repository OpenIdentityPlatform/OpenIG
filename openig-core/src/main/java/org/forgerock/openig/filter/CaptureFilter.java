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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.http.util.StandardCharsets.UTF_8;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.JsonValues.evaluate;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Captures request and response messages for further analysis.
 * @deprecated since OpenIG 3.1
 */
@Deprecated
public class CaptureFilter extends GenericHeapObject implements Filter {

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
         * {@link FileWriterProvider#FileWriterProvider(java.io.File, java.nio.charset.Charset)}
         * with {@literal UTF-8} as {@link java.nio.charset.Charset}.
         *
         * @param file
         *            specify where the output will be flushed.
         */
        public FileWriterProvider(final File file) {
            this(file, UTF_8);
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
    private static final Set<String> TEXT_TYPES = new HashSet<String>(
            Arrays.asList("application/atom+xml", "application/javascript", "application/json",
                    "application/rss+xml", "application/xhtml+xml", "application/xml", "application/xml-dtd",
                    "application/x-www-form-urlencoded")
    ); // make all entries lower case

    private Expression<Boolean> condition = null;

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
    public synchronized void setCondition(final Expression<Boolean> condition) {
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
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        Exchange exchange = context.asContext(Exchange.class);
        Object eval = (condition != null ? condition.eval(exchange) : Boolean.TRUE);
        boolean doCapture = (eval instanceof Boolean && (Boolean) eval);

        // Exit fast if we have nothing to do
        if (!doCapture) {
            return next.handle(context, request);
        }

        final long id = sequence.incrementAndGet();
        captureRequest(request, id);
        return next.handle(context, request)
                .thenOnResult(new ResultHandler<Response>() {
                    @Override
                    public void handleResult(final Response result) {
                        captureResponse(result, id);
                    }
                });
    }

    private void captureRequest(Request request, long id) {
        try {
            PrintWriter writer = provider.getWriter();
            writer.println();
            writer.println("--- REQUEST " + id + " --->");
            writer.println();
            writer.println(request.getMethod() + " " + request.getUri() + " " + request.getVersion());
            writeHeaders(writer, request);
            writeEntity(writer, request);
            writer.flush();
        } catch (IOException e) {
            // Just print a warning, do not abort message processing
            logger.warning("Can't print request message for exchange " + id);
            logger.debug(e);
        }
    }

    private void captureResponse(Response response, long id) {
        try {
            PrintWriter writer = provider.getWriter();
            writer.println();
            writer.println("<--- RESPONSE " + id + " ---");
            writer.println();
            writer.print(response.getVersion() + " ");
            if (response.getStatus() != null) {
                writer.print(response.getStatus().getCode() + " ");
                writer.print(response.getStatus().getReasonPhrase());
            }
            writer.println();
            writeHeaders(writer, response);
            writeEntity(writer, response);
            writer.flush();
        } catch (IOException e) {
            // Just print a warning, do not abort message processing
            logger.warning("Can't print response message for exchange " + id);
            logger.debug(e);

        }
    }

    private void writeHeaders(final PrintWriter writer, Message message) {
        for (String key : message.getHeaders().keySet()) {
            for (String value : message.getHeaders().get(key)) {
                writer.println(key + ": " + value);
            }
        }
    }

    private void writeEntity(final PrintWriter writer, Message message) throws IOException {
        ContentTypeHeader contentType = ContentTypeHeader.valueOf(message);
        if (message.getEntity() == null || contentType.getType() == null) {
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
            message.getEntity().push();
            try {
                message.getEntity().copyDecodedContentTo(writer);
            } finally {
                message.getEntity().pop();
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
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            CaptureFilter filter = new CaptureFilter();
            filter.setWriterProvider(buildFileProvider(config));
            filter.setCondition(asExpression(config.get("condition"), Boolean.class));
            JsonValue capture = config.get("captureEntity");
            filter.setCaptureEntity(capture.defaultTo(filter.captureEntity).asBoolean());
            return filter;
        }

        private WriterProvider buildFileProvider(final JsonValue config) {
            File file = new File(evaluate(config.get("file").required()));
            Charset charset = config.get("charset").defaultTo("UTF-8").asCharset();
            return new FileWriterProvider(file, charset);
        }
    }
}
