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

package org.forgerock.openig.handler;

import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.expression;

import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.util.CaseInsensitiveMap;
import org.forgerock.http.util.MultiValueMap;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Creates a static HTTP response.
 */
public class StaticResponseHandler extends GenericHeapObject implements Handler {

    /** The status (code + reason). */
    private final Status status;

    /** Protocol version (e.g. {@code "HTTP/1.1"}. */
    private final String version;

    /** Message header fields whose values are expressions that are evaluated. */
    private final MultiValueMap<String, Expression<String>> headers =
            new MultiValueMap<>(new CaseInsensitiveMap<List<Expression<String>>>());

    /** The message entity expression. */
    private final Expression<String> entity;

    /**
     * Constructor.
     *
     * @param status
     *            The response status to set.
     */
    public StaticResponseHandler(final Status status) {
        this(status, null, null);
    }

    /**
     * Constructor.
     *
     * @param status
     *            The response status to set.
     * @param version
     *            The protocol version.
     * @param entity
     *            The message entity expression.
     */
    public StaticResponseHandler(final Status status,
                                 final String version,
                                 final Expression<String> entity) {
        this.status = status;
        this.version = version;
        this.entity = entity;
    }

    /**
     * Adds a pair key / expression to the header.
     *
     * @param key
     *            The header key.
     * @param expression
     *            The expression to evaluate.
     * @return The current static response handler.
     */
    public StaticResponseHandler addHeader(final String key, final Expression<String> expression) {
        headers.add(key, expression);
        return this;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Bindings bindings = bindings(context, request);
        Response response = new Response();
        response.setStatus(this.status);
        if (this.version != null) { // default in Message class
            response.setVersion(this.version);
        }
        for (String key : this.headers.keySet()) {
            for (Expression<String> expression : this.headers.get(key)) {
                String eval = expression.eval(bindings);
                if (eval != null) {
                    response.getHeaders().add(key, eval);
                }
            }
        }
        if (entity != null) {
            // use content-type charset (or default)
            response.setEntity(entity.eval(bindings));
        }
        return Promises.newResultPromise(response);
    }

    /**
     * Creates and initializes a static response handler in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final int code = config.get("status").as(evaluatedWithHeapProperties()).required().asInteger();
            final String reason = config.get("reason").as(evaluatedWithHeapProperties()).asString();
            Status status = Status.valueOf(code, reason);
            final String version = config.get("version").as(evaluatedWithHeapProperties()).asString();
            final JsonValue headers = config.get("headers").expect(Map.class);
            final Expression<String> entity = config.get("entity").as(expression(String.class));
            final StaticResponseHandler handler = new StaticResponseHandler(status, version, entity);
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonValue value : headers.get(key).expect(List.class)) {
                        handler.addHeader(key, value.required().as(expression(String.class)));
                    }
                }
            }
            return handler;
        }
    }
}
