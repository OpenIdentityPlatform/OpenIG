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
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

// JSON Fluent
import org.forgerock.json.fluent.JsonNode;
import org.forgerock.json.fluent.JsonNodeException;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveMap;
import org.forgerock.openig.util.JsonNodeUtil;
import org.forgerock.openig.util.MultiValueMap;

/**
 * Creates a new request with in the exchange object. It will replace any request that may
 * already be present in the exchange. The request can include a form, specified in the
 * {@code form} field, which is included in an entity encoded in
 * {@code application/x-www-form-urlencoded} format if request method is {@code POST}, or
 * otherwise as (additional) query parameters in the URI.
 *
 * @author Paul C. Bryan
 */
public class StaticRequestFilter extends GenericFilter {

    /** The HTTP method to be performed on the resource. */
    public String method;

    /** The fully-qualified URI of the resource being accessed. */
    public URI uri;

    /** Protocol version (e.g.&nbsp{@code "HTTP/1.1"}). */
    public String version;

    /** Message header fields whose values are expressions that are evaluated. */
    public final MultiValueMap<String, Expression> headers =
     new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /** A form to include in the request, whose values are exchange-scoped expressions that are evaluated. */
    public final MultiValueMap<String, Expression> form =
     new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /**
     * Filters the exchange by creating a new request, replacing any request in the exchange.
     */
    @Override
    public void filter(Exchange exchange, Chain chain) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Request request = new Request();
        request.method = this.method;
        request.uri = this.uri;
        if (this.version != null) { // default in Message class
            request.version = version;
        }
        for (String key : this.headers.keySet()) {
            for (Expression expression : this.headers.get(key)) {
                String eval = expression.eval(exchange, String.class);
                if (eval != null) {
                    request.headers.add(key, eval);
                }
            }
        }
        if (this.form != null) {
            Form f = new Form();
            for (String key : this.form.keySet()) {
                for (Expression expression : this.form.get(key)) {
                    String eval = expression.eval(exchange, String.class);
                    if (eval != null) {
                        f.add(key, eval);
                    }
                }
            }
            if (request.method.equals("POST")) {
                f.toRequestEntity(request);
            } else {
                f.appendRequestQuery(request);
            }
        }
        exchange.request = request;
        chain.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes a request filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonNodeException {
            StaticRequestFilter filter = new StaticRequestFilter();
            filter.method = config.get("method").required().asString(); // required
            filter.uri = config.get("uri").required().asURI(); // required
            filter.version = config.get("version").asString(); // optional
            JsonNode headers = config.get("headers").expect(Map.class); // optional
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonNode value : headers.get(key).required().expect(List.class)) {
                        filter.headers.add(key, JsonNodeUtil.asExpression(value.required()));
                    }
                }
            }
            JsonNode form = config.get("form").expect(Map.class); // optional
            if (form != null) {
                for (String key : form.keys()) {
                    for (JsonNode value : form.get(key).required().expect(List.class)) {
                        filter.form.add(key, JsonNodeUtil.asExpression(value.required()));
                    }
                }
            }
            return filter;
        }
    }
}
