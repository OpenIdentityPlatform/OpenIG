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

package org.forgerock.openig.handler;

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
    public Integer status;

    /** The response status reason (e.g. "OK"). */
    public String reason;

    /** Protocol version (e.g. {@code "HTTP/1.1"}. */
    public String version = null;

    /** Message header fields whose values are expressions that are evaluated. */
    public final MultiValueMap<String, Expression> headers =
            new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /** The message entity. */
    public String entity = null;

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Response response = new Response();
        response.status = this.status;
        response.reason = this.reason;
        if (response.reason == null) {
            // not explicit, derive from status
            response.reason = HttpUtil.getReason(response.status);
        }
        if (response.reason == null) {
            // couldn't derive from status; say something
            response.reason = "Uncertain";
        }
        if (this.version != null) { // default in Message class
            response.version = this.version;
        }
        for (String key : this.headers.keySet()) {
            for (Expression expression : this.headers.get(key)) {
                String eval = expression.eval(exchange, String.class);
                if (eval != null) {
                    response.headers.add(key, eval);
                }
            }
        }
        if (this.entity != null) {
            // use content-type charset (or default)
            HttpUtil.toEntity(response, entity, null);
        }
        // finally replace response in the exchange
        exchange.response = response;
        timer.stop();
    }

    /**
     * Creates and initializes a static response handler in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            StaticResponseHandler handler = new StaticResponseHandler();
            handler.status = config.get("status").required().asInteger();
            handler.reason = config.get("reason").asString();
            handler.version = config.get("version").asString();
            JsonValue headers = config.get("headers").expect(Map.class);
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonValue value : headers.get(key).expect(List.class)) {
                        handler.headers.add(key, JsonValueUtil.asExpression(value.required()));
                    }
                }
            }
            handler.entity = config.get("entity").asString();
            return handler;
        }
    }
}
