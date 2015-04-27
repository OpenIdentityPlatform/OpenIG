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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.capture;

import static groovy.json.JsonOutput.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.Logger;

/**
 * Capture a message.
 */
public class MessageCapture {

    /** Set of common textual content with non-text content-types to capture. */
    private static final Set<String> TEXT_TYPES = new HashSet<String>(
            Arrays.asList("application/atom+xml", "application/javascript", "application/json",
                          "application/rss+xml", "application/xhtml+xml", "application/xml", "application/xml-dtd",
                          "application/x-www-form-urlencoded")
    ); // make all entries lower case

    private final Logger logger;
    private final boolean captureEntity;
    private final boolean captureExchange;

    /**
     * Builds a MessageCapture that will prints messages in the provided {@code logger}.
     *
     * @param logger
     *         where to write captured messages
     * @param captureEntity
     *         capture the entity content (if not binary)
     */
    public MessageCapture(final Logger logger, final boolean captureEntity) {
        this(logger, captureEntity, false);
    }

    /**
     * Builds a MessageCapture that will prints messages in the provided {@code logger}.
     *
     * @param logger
     *         where to write captured messages
     * @param captureEntity
     *         capture the entity content (if not binary)
     * @param captureExchange
     *         capture the exchange content (excluding request and response object) as json
     */
    public MessageCapture(final Logger logger, final boolean captureEntity, final boolean captureExchange) {
        this.logger = logger;
        this.captureEntity = captureEntity;
        this.captureExchange = captureExchange;
    }

    /**
     * Captures the given exchanges, in the given mode. The provided mode helps to determine if the interesting bits are
     * in the request or in the response of the exchange.
     *
     * @param exchange
     *         contains the captured messages
     * @param mode
     *         one of {@link CapturePoint#REQUEST},  {@link CapturePoint#FILTERED_REQUEST},
     *         {@link CapturePoint#FILTERED_RESPONSE} or {@link CapturePoint#RESPONSE}
     */
    public void capture(final Exchange exchange, final CapturePoint mode) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        int exchangeId = System.identityHashCode(exchange);
        switch (mode) {
        case REQUEST:
            captureRequest(writer, exchange.request, exchangeId);
            break;
        case FILTERED_REQUEST:
            captureFilteredRequest(writer, exchange.request, exchangeId);
            break;
        case RESPONSE:
            captureResponse(writer, exchange.response, exchangeId);
            break;
        case FILTERED_RESPONSE:
            captureFilteredResponse(writer, exchange.response, exchangeId);
            break;
        default:
            throw new IllegalArgumentException("The given mode is not accepted: " + mode.name());
        }

        // Prints the exchange if required
        if (captureExchange) {
            writer.println("Exchange's content as JSON (without request/response):");
            captureExchangeAsJson(writer, exchange);
        }

        // Print the message
        logger.info(out.toString());
    }

    void capture(final Exchange exchange, Request request, final CapturePoint mode) {
        // FIXME Compat
        exchange.request = request;
        capture(exchange, mode);
    }

    void capture(final Exchange exchange, Response response, final CapturePoint mode) {
        // FIXME Compat
        exchange.response = response;
        capture(exchange, mode);
    }

    private void captureExchangeAsJson(final PrintWriter writer, final Exchange exchange) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(exchange);
        map.remove("exchange");
        map.remove("request");
        map.remove("response");
        map.remove("javax.servlet.http.HttpServletRequest");
        map.remove("javax.servlet.http.HttpServletResponse");
        writer.println(prettyPrint(toJson(map)));
    }

    private void captureRequest(PrintWriter writer, Request request, long id) {
        writer.printf("%n%n--- (request) exchange:%d --->%n%n", id);
        if (request != null) {
            captureRequestMessage(writer, request);
        }
    }

    private void captureFilteredRequest(PrintWriter writer, Request request, long id) {
        writer.printf("%n%n--- (filtered-request) exchange:%d --->%n%n", id);
        if (request != null) {
            captureRequestMessage(writer, request);
        }
    }

    private void captureResponse(PrintWriter writer, Response response, long id) {
        writer.printf("%n%n<--- (response) exchange:%d ---%n%n", id);
        if (response != null) {
            captureResponseMessage(writer, response);
        }
    }

    private void captureFilteredResponse(PrintWriter writer, Response response, long id) {
        writer.printf("%n%n<--- (filtered-response) exchange:%d ---%n%n", id);
        if (response != null) {
            captureResponseMessage(writer, response);
        }
    }

    private void captureRequestMessage(final PrintWriter writer, Request request) {
        writer.println(request.getMethod() + " " + request.getUri() + " " + request.getVersion());
        writeHeaders(writer, request);
        writeEntity(writer, request);
        writer.flush();
    }

    private void captureResponseMessage(final PrintWriter writer, Response response) {
        writer.println(response.getVersion() + " " + response.getStatus() + " " + response.getReason());
        writeHeaders(writer, response);
        writeEntity(writer, response);
        writer.flush();
    }

    private void writeHeaders(final PrintWriter writer, Message message) {
        for (String key : message.getHeaders().keySet()) {
            for (String value : message.getHeaders().get(key)) {
                writer.println(key + ": " + value);
            }
        }
    }

    private void writeEntity(final PrintWriter writer, Message message) {
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
        } catch (IOException e) {
            writer.println("[IOException during entity writing] - " + e.getMessage());
        }
        // entity may not terminate with new line, so here it is
        writer.println();
    }

    /**
     * Decide if the given content-type is printable or not. The entity represents a textual/printable content if: <ul>
     * <li>there is a charset associated to the content-type, we'll be able to print it correctly</li> <li>the content
     * type is in the category 'text' or it is an accepted type</li> </ul>
     *
     * @param contentType
     *         the message's content-type
     * @return {@literal true} if the content-type represents a textual content
     */
    private static boolean isTextualContent(final ContentTypeHeader contentType) {
        String type = (contentType.getType() != null ? contentType.getType().toLowerCase() : null);
        return contentType.getCharset() != null
                // text or white-listed type
                || (type != null && (TEXT_TYPES.contains(type) || type.startsWith("text/")));
    }

}
