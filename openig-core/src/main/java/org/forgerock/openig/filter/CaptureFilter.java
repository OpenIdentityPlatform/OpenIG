/* * The contents of this file are subject to the terms of the Common Development and
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
 * Portions Copyrighted 2011-2012 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.header.ContentTypeHeader;
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

    /** Set of common textual content with non-text content-types to capture. */
    private static final HashSet<String> TEXT_TYPES = new HashSet<String>(
            Arrays.asList("application/atom+xml", "application/javascript", "application/json",
                    "application/rss+xml", "application/xhtml+xml", "application/xml", "application/xml-dtd",
                    "application/x-www-form-urlencoded")
    ); // make all entries lower case

    /** File where captured output should be written. */
    public File file;

    /** Character set to encode captured output with (default: UTF-8). */
    public Charset charset = Charset.forName("UTF-8");

    /**
     * Condition to evaluate to determine whether to capture an exchange (default: {@code null} a.k.a. unconditional).
     */
    public Expression condition = null;

    /** Indicates message entity should be captured (default: {@code true}). */
    public boolean captureEntity = true;

    /** Name of this capture filter instance. */
    public String instance = getClass().getSimpleName();

    /** Used to assign each exchange a monotonically increasing number. */
    private AtomicLong sequence = new AtomicLong(0L);

    /** Used to write captured output to file. */
    private PrintWriter writer;

    /**
     * Filters the exchange by capturing request and response messages.
     */
    @Override
    public synchronized void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Object eval = (condition != null ? condition.eval(exchange) : Boolean.TRUE);
        boolean doCapture = (eval instanceof Boolean && (Boolean) eval);
        long id = 0;
        if (doCapture) {
            id = sequence.incrementAndGet();
            checkWriter();
            captureRequest(exchange.request, id);
        }
        next.handle(exchange);
        if (doCapture) {
            checkWriter();
            captureResponse(exchange.response, id);
        }
        timer.stop();
    }

    private void checkWriter() throws FileNotFoundException {

        if (writer == null || !file.exists()) {
            if (writer != null) { // file was removed while open
                writer.close();
            }
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
        }
    }

    private void captureRequest(Request request, long id) throws IOException {
        writer.println();
        writer.println("--- REQUEST " + id + " --->");
        writer.println();
        writer.println(request.method + " " + request.uri + " " + request.version);
        writeHeaders(request);
        writeEntity(request);
        writer.flush();
    }

    private void captureResponse(Response response, long id) throws IOException {
        writer.println();
        writer.println("<--- RESPONSE " + id + " ---");
        writer.println();
        writer.println(response.version + " " + response.status + " " + response.reason);
        writeHeaders(response);
        writeEntity(response);
        writer.flush();
    }

    private void writeHeaders(Message message) throws IOException {
        for (String key : message.headers.keySet()) {
            for (String value : message.headers.get(key)) {
                writer.println(key + ": " + value);
            }
        }
    }

    private void writeEntity(Message message) throws IOException {
        ContentTypeHeader contentType = new ContentTypeHeader(message);
        if (message.entity == null || contentType.type == null) {
            return;
        }
        writer.println();
        if (!captureEntity) { // simply show presence of an entity
            writer.println("[entity]");
            return;
        }
        String type = (contentType.type != null ? contentType.type.toLowerCase() : null);
        if (!(contentType.charset != null || (type != null && // text or whitelisted type
                (TEXT_TYPES.contains(type) || type.startsWith("text/"))))) {
            writer.println("[binary entity]");
            return;
        }
        try {
            Reader reader = HttpUtil.entityReader(message, true, null);
            try {
                Streamer.stream(reader, writer);
            } finally {
                try {
                    reader.close();
                } catch (IOException ioe) {
                    // suppress this exception
                }
            }
        } catch (UnsupportedEncodingException uee) {
            writer.println("[entity contains data in unsupported encoding]");
        } catch (UnsupportedCharsetException uce) {
            writer.println("[entity contains characters in unsupported character set]");
        } catch (IllegalCharsetNameException icne) {
            writer.println("[entity contains characters in illegal character set]");
        }
        writer.println(); // entity may not terminate with new line, so here it is
    }

    /** Creates and initializes a capture filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            CaptureFilter filter = new CaptureFilter();
            filter.file = config.get("file").required().asFile(); // required
            filter.charset = config.get("charset").defaultTo("UTF-8").asCharset(); // optional
            filter.condition = JsonValueUtil.asExpression(config.get("condition")); // optional
            filter.captureEntity = config.get("captureEntity").defaultTo(filter.captureEntity).asBoolean(); // optional
            filter.instance = super.name;
            return filter;
        }
    }
}
