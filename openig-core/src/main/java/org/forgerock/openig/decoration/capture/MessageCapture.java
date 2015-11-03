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

import static groovy.json.JsonOutput.prettyPrint;
import static groovy.json.JsonOutput.toJson;

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
import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;

/**
 * Capture a message.
 */
public class MessageCapture {

    /** Set of common textual content with non-text content-types to capture. */
    private static final Set<String> TEXT_TYPES = new HashSet<>(
            Arrays.asList("application/atom+xml", "application/javascript", "application/json",
                          "application/rss+xml", "application/xhtml+xml", "application/xml", "application/xml-dtd",
                          "application/x-www-form-urlencoded")
    ); // make all entries lower case

    private final Logger logger;
    private final boolean captureEntity;
    private final boolean captureContext;

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
     * @param captureContext
     *         capture the context content (excluding request and response object) as json
     */
    public MessageCapture(final Logger logger, final boolean captureEntity, final boolean captureContext) {
        this.logger = logger;
        this.captureEntity = captureEntity;
        this.captureContext = captureContext;
    }

    /**
     * Captures the given request, in the given mode.
     *
     * @param context
     *         Captured message's {@link Context}
     * @param request
     *         Captured message
     * @param mode
     *         one of {@link CapturePoint#REQUEST},  {@link CapturePoint#FILTERED_REQUEST}
     */
    void capture(final Context context, final Request request, final CapturePoint mode) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        String id = context.getId();
        switch (mode) {
        case REQUEST:
            captureRequest(writer, request, id);
            break;
        case FILTERED_REQUEST:
            captureFilteredRequest(writer, request, id);
            break;
        default:
            throw new IllegalArgumentException("The given mode is not accepted: " + mode.name());
        }

        // Prints the context if required
        if (captureContext) {
            writer.println("Context's content as JSON:");
            captureContextAsJson(writer, context);
        }

        // Print the message
        logger.info(out.toString());
    }

    /**
     * Captures the given response, in the given mode.
     *
     * @param context
     *         Captured message's {@link Context}
     * @param response
     *         Captured message
     * @param mode
     *         one of {@link CapturePoint#FILTERED_RESPONSE} or {@link CapturePoint#RESPONSE}
     */
    void capture(final Context context, final Response response, final CapturePoint mode) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        String id = context.getId();
        switch (mode) {
        case RESPONSE:
            captureResponse(writer, response, id);
            break;
        case FILTERED_RESPONSE:
            captureFilteredResponse(writer, response, id);
            break;
        default:
            throw new IllegalArgumentException("The given mode is not accepted: " + mode.name());
        }

        // Prints the context if required
        if (captureContext) {
            writer.println("Context's content as JSON:");
            captureContextAsJson(writer, context);
        }

        // Print the message
        logger.info(out.toString());
    }

    private void captureContextAsJson(final PrintWriter writer, final Context context) {
        // TODO we restrict ourselves to attributes only here, we should pretty print the chain of contexts instead
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        Map<String, Object> map = new LinkedHashMap<>(attributesContext.getAttributes());
        map.remove("javax.servlet.http.HttpServletRequest");
        map.remove("javax.servlet.http.HttpServletResponse");
        writer.println(prettyPrint(toJson(map)));
    }

    private void captureRequest(PrintWriter writer, Request request, String id) {
        writer.printf("%n%n--- (request) id:%s --->%n%n", id);
        if (request != null) {
            captureRequestMessage(writer, request);
        }
    }

    private void captureFilteredRequest(PrintWriter writer, Request request, String id) {
        writer.printf("%n%n--- (filtered-request) id:%s --->%n%n", id);
        if (request != null) {
            captureRequestMessage(writer, request);
        }
    }

    private void captureResponse(PrintWriter writer, Response response, String id) {
        writer.printf("%n%n<--- (response) id:%s ---%n%n", id);
        if (response != null) {
            captureResponseMessage(writer, response);
        }
    }

    private void captureFilteredResponse(PrintWriter writer, Response response, String id) {
        writer.printf("%n%n<--- (filtered-response) id:%s ---%n%n", id);
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
        writer.print(response.getVersion() + " ");
        if (response.getStatus() != null) {
            writer.print(response.getStatus().getCode() + " ");
            writer.print(response.getStatus().getReasonPhrase());
        }
        writer.println();
        writeHeaders(writer, response);
        writeEntity(writer, response);
        writer.flush();
    }

    private void writeHeaders(final PrintWriter writer, Message message) {
        for (Map.Entry<String, Header> entry : message.getHeaders().asMapOfHeaders().entrySet()) {
            for (String value : entry.getValue().getValues()) {
                writer.println(entry.getKey() + ": " + value);
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
