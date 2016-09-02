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

package org.forgerock.openig.filter;

import static org.forgerock.json.JsonValueFunctions.charset;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.pattern;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.expression;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.regex.PatternTemplate;
import org.forgerock.openig.regex.StreamPatternExtractor;
import org.forgerock.openig.util.MessageType;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts regular expression patterns from a message entity. Extraction occurs either
 * before the request is handled if {@code messageType} is {@link MessageType#REQUEST}, or
 * after the request is handled if it is {@link MessageType#RESPONSE}. Each pattern can have
 * an associated template, which is applied to its match result.
 * <p>
 * The extraction results are contained in a {@link Map} object, whose location is specified
 * by the {@code target} expression. For a given matched pattern, the value stored in the map
 * is either the result of applying its associated pattern template (if specified) or the
 * match result itself otherwise.
 *
 * @see StreamPatternExtractor
 * @see PatternTemplate
 */
public class EntityExtractFilter extends GenericHeapObject implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(EntityExtractFilter.class);

    /** Extracts regular expression patterns from entities. */
    private final StreamPatternExtractor extractor = new StreamPatternExtractor();

    /** The message type to extract patterns from. */
    private final MessageType messageType;

    /** Overrides the character set encoding specified in message. If {@code null}, the message encoding is used. */
    private final Charset charset;

    /** Expression that yields the target object that will contain the mapped extraction results. */
    private final Expression<?> target;

    /**
     * Builds an EntityExtractFilter that will act either on {@link MessageType#REQUEST} or {@link MessageType#RESPONSE}
     * flow, extracting patterns into the given {@code target} {@link Expression}. The {@link Charset} used is the one
     * of the message.
     *
     * @param type
     *         Specifies the execution flow to be executed in
     * @param target
     *         Expression that yields the target object that will contain the mapped extraction results
     */
    public EntityExtractFilter(final MessageType type, final Expression<?> target) {
        this(type, target, null);
    }

    /**
     * Builds an EntityExtractFilter that will act either on {@link MessageType#REQUEST} or {@link MessageType#RESPONSE}
     * flow, extracting patterns into the given {@code target} {@link Expression}. The {@link Charset} used is the one
     * specified.
     *
     * @param type
     *         Specifies the execution flow to be executed in
     * @param target
     *         Expression that yields the target object that will contain the mapped extraction results
     * @param charset
     *         Overrides the character set encoding specified in message. If {@code null}, the message encoding is used
     */
    public EntityExtractFilter(final MessageType type, final Expression<?> target, final Charset charset) {
        this.messageType = type;
        this.target = target;
        this.charset = charset;
    }

    /**
     * Returns the regular expression patterns extractor.
     * @return the regular expression patterns extractor.
     */
    public StreamPatternExtractor getExtractor() {
        return extractor;
    }

    private void process(Bindings bindings, Message message) {
        Map<String, String> map = new HashMap<>();
        if (message != null) {
            try {
                try (Reader reader = message.getEntity().newDecodedContentReader(charset)) {
                    // get 'em all now
                    for (Map.Entry<String, String> match : extractor.extract(reader)) {
                        map.put(match.getKey(), match.getValue());
                    }
                }
            } catch (IOException ioe) {
                logger.trace("An error occurred while reading the entity's message", ioe);
                // may yield partial or unresolved attributes
            }
        }
        target.set(bindings, map);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        if (messageType == MessageType.REQUEST) {
            process(bindings(context, request), request);
        }
        Promise<Response, NeverThrowsException> promise = next.handle(context, request);
        if (messageType == MessageType.RESPONSE) {
            return promise.thenOnResult(new ResultHandler<Response>() {
                @Override
                public void handleResult(final Response response) {
                    process(bindings(context, request, response), response);
                }
            });
        }
        return promise;
    }

    /** Creates and initializes an entity extract handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            EntityExtractFilter filter = new EntityExtractFilter(
                    config.get("messageType")
                          .as(evaluatedWithHeapProperties())
                          .required()
                          .as(enumConstant(MessageType.class)),
                    config.get("target").required().as(expression(Object.class)),
                    config.get("charset").as(evaluatedWithHeapProperties()).as(charset()));

            for (JsonValue jv : config.get("bindings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                String key = jv.get("key").as(evaluatedWithHeapProperties()).required().asString();
                if (filter.extractor.getPatterns().containsKey(key)) {
                    throw new JsonValueException(jv.get("key"), "Key already defined (after evaluation : " + key + ")");
                }
                filter.extractor.getPatterns().put(key, jv.get("pattern").required().as(pattern()));
                String template = jv.get("template").asString();
                if (template != null) {
                    filter.extractor.getTemplates().put(key, new PatternTemplate(template));
                }
            }
            return filter;
        }
    }
}
