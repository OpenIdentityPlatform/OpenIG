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

import static org.forgerock.openig.util.JsonValues.asExpression;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.util.MessageType;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Removes headers from and adds headers to a message.
 */
public class HeaderFilter extends GenericHeapObject implements Filter {

    /** Indicates the type of message in the exchange to filter headers for. */
    private final MessageType messageType;

    /** The names of header fields to remove from the message. */
    private final CaseInsensitiveSet removedHeaders = new CaseInsensitiveSet();

    /** Header fields to add to the message. */
    private final Headers addedHeaders = new Headers();

    /**
     * Builds a HeaderFilter processing either the incoming or outgoing message.
     * @param messageType {@link MessageType#REQUEST} or {@link MessageType#RESPONSE}
     */
    public HeaderFilter(final MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * Returns the names of header fields to remove from the message.
     * @return the names of header fields to remove from the message.
     */
    public CaseInsensitiveSet getRemovedHeaders() {
        return removedHeaders;
    }

    /**
     * Returns the header fields to add to the message.
     * This is a essentially a Map of String to a List of String, each listed value representing
     * an expression that will be evaluated.
     * @return the header fields to add to the message.
     */
    public Headers getAddedHeaders() {
        return addedHeaders;
    }

    /**
     * Removes all specified headers, then adds all specified headers.
     */
    private void process(Message message, Exchange exchange) {
        for (String s : this.removedHeaders) {
            message.getHeaders().remove(s);
        }
        for (String key : this.addedHeaders.keySet()) {
            for (String value : this.addedHeaders.get(key).getValues()) {
                JsonValue jsonValue = new JsonValue(value);
                message.getHeaders().add(key, asExpression(jsonValue, String.class).eval(exchange));
            }
        }
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final Exchange exchange = context.asContext(Exchange.class);
        if (messageType == MessageType.REQUEST) {
            process(request, exchange);
        }
        Promise<Response, NeverThrowsException> promise = next.handle(context, request);
        if (messageType == MessageType.RESPONSE) {
            return promise.thenOnResult(new ResultHandler<Response>() {
                @Override
                public void handleResult(final Response response) {
                    process(response, exchange);
                }
            });
        }
        return promise;
    }

    /** Creates and initializes a header filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            HeaderFilter filter = new HeaderFilter(config.get("messageType")
                                                         .required()
                                                         .asEnum(MessageType.class));
            filter.removedHeaders.addAll(config.get("remove")
                                         .defaultTo(Collections.emptyList())
                                         .asList(String.class));
            JsonValue add = config.get("add")
                    .defaultTo(Collections.emptyMap())
                    .expect(Map.class);
            for (String key : add.keys()) {
                List<String> values = add.get(key).required().asList(String.class);
                filter.addedHeaders.put(key, values);
            }
            return filter;
        }
    }
}
