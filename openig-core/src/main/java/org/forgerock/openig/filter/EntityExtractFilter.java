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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.openig.util.JsonValueUtil.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.regex.PatternTemplate;
import org.forgerock.openig.regex.StreamPatternExtractor;

/**
 * Extracts regular expression patterns from a message entity. Extraction occurs either
 * before the exchange is handled if {@code messageType} is {@link MessageType#REQUEST}, or
 * after the exchange is handled if it is {@link MessageType#RESPONSE}. Each pattern can have
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
public class EntityExtractFilter extends GenericFilter {

    /** Extracts regular expression patterns from entities. */
    private final StreamPatternExtractor extractor = new StreamPatternExtractor();

    /** The message type in the exchange to extract patterns from. */
    private final MessageType messageType;

    /** Overrides the character set encoding specified in message. If {@code null}, the message encoding is used. */
    private final Charset charset;

    /** Expression that yields the target object that will contain the mapped extraction results. */
    private final Expression target;

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
    public EntityExtractFilter(final MessageType type, final Expression target) {
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
    public EntityExtractFilter(final MessageType type, final Expression target, final Charset charset) {
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

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        if (messageType == MessageType.REQUEST) {
            process(exchange, exchange.request);
        }
        next.handle(exchange);
        if (messageType == MessageType.RESPONSE) {
            process(exchange, exchange.response);
        }
        timer.stop();
    }

    private void process(Exchange exchange, Message message) {
        HashMap<String, String> map = new HashMap<String, String>();
        if (message != null && message.entity != null) {
            try {
                Reader reader = HttpUtil.entityReader(message, true, charset);
                try {
                    // get 'em all now
                    for (Map.Entry<String, String> match : extractor.extract(reader)) {
                        map.put(match.getKey(), match.getValue());
                    }
                } finally {
                    reader.close();
                }
            } catch (IOException ioe) {
                // may yield partial or unresolved attributes
            }
        }
        target.set(exchange, map);
    }

    /** Creates and initializes an entity extract handler in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            EntityExtractFilter filter = new EntityExtractFilter(config.get("messageType")
                                                                         .required()
                                                                         .asEnum(MessageType.class),
                                                                 asExpression(config.get("target").required()),
                                                                 config.get("charset").asCharset());

            for (JsonValue jv : config.get("bindings").required().expect(List.class)) {
                jv.required().expect(Map.class);
                String key = jv.get("key").required().asString();
                if (filter.extractor.getPatterns().containsKey(key)) {
                    throw new JsonValueException(jv.get("key"), "Key already defined");
                }
                filter.extractor.getPatterns().put(key, jv.get("pattern").required().asPattern());
                String template = jv.get("template").asString();
                if (template != null) {
                    filter.extractor.getTemplates().put(key, new PatternTemplate(template));
                }
            }
            return filter;
        }
    }
}
