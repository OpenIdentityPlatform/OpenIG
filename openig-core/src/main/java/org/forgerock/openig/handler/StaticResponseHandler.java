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

package org.forgerock.openig.handler;

import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveMap;
import org.forgerock.openig.util.JsonValueUtil;
import org.forgerock.openig.util.MultiValueMap;

/**
 * Creates a static response in an HTTP exchange.
 */
public class StaticResponseHandler extends GenericHandler {

    /** The response status code (e.g. 200). */
    private final Integer status;

    /** The response status reason (e.g. "OK"). */
    private final String reason;

    /** Protocol version (e.g. {@code "HTTP/1.1"}. */
    private final String version;

    /** Message header fields whose values are expressions that are evaluated. */
    private final MultiValueMap<String, Expression> headers =
            new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /** The message entity expression. */
    private final Expression entity;

    /**
     * Constructor.
     *
     * @param status
     *            The response status to set.
     * @param reason
     *            The response status reason to set.
     */
    public StaticResponseHandler(final Integer status, final String reason) {
        this(status, reason, null, null);
    }

    /**
     * Constructor.
     *
     * @param status
     *            The response status to set.
     * @param reason
     *            The response status reason to set.
     * @param version
     *            The protocol version.
     * @param entity
     *            The message entity expression.
     */
    public StaticResponseHandler(final Integer status, final String reason, final String version,
            final Expression entity) {
        super();
        this.status = status;
        this.reason = reason;
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
    public StaticResponseHandler addHeader(final String key, final Expression expression) {
        headers.add(key, expression);
        return this;
    }

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Response response = new Response();
        response.setStatus(this.status);
        response.setReason(this.reason);
        if (response.getReason() == null) {
            // not explicit, derive from status
            response.setReason(HttpUtil.getReason(response.getStatus()));
        }
        if (response.getReason() == null) {
            // couldn't derive from status; say something
            response.setReason("Uncertain");
        }
        if (this.version != null) { // default in Message class
            response.setVersion(this.version);
        }
        for (String key : this.headers.keySet()) {
            for (Expression expression : this.headers.get(key)) {
                String eval = expression.eval(exchange, String.class);
                if (eval != null) {
                    response.getHeaders().add(key, eval);
                }
            }
        }
        if (entity != null) {
            // use content-type charset (or default)
            response.setEntity(entity.eval(exchange, String.class));
        }
        // finally replace response in the exchange
        closeSilently(exchange.response);
        exchange.response = response;
        timer.stop();
    }

    /**
     * Creates and initializes a static response handler in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            final int status = config.get("status").required().asInteger();
            final String reason = config.get("reason").asString();
            final String version = config.get("version").asString();
            final JsonValue headers = config.get("headers").expect(Map.class);
            final Expression entity = JsonValueUtil.asExpression(config.get("entity"));
            final StaticResponseHandler handler = new StaticResponseHandler(status, reason, version, entity);
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonValue value : headers.get(key).expect(List.class)) {
                        handler.addHeader(key, JsonValueUtil.asExpression(value.required()));
                    }
                }
            }
            return handler;
        }
    }
}
