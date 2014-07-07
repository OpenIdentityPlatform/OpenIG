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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveMap;
import org.forgerock.openig.util.MultiValueMap;

/**
 * Creates a new request with in the exchange object. It will replace any request that may
 * already be present in the exchange. The request can include a form, specified in the
 * {@code form} field, which is included in an entity encoded in
 * {@code application/x-www-form-urlencoded} format if request method is {@code POST}, or
 * otherwise as (additional) query parameters in the URI.
 */
public class StaticRequestFilter extends GenericFilter {

    /** The HTTP method to be performed on the resource. */
    private final String method;

    /** URI as an expression to allow dynamic URI construction. */
    private Expression uri;

    /** Protocol version (e.g. {@code "HTTP/1.1"}). */
    private String version;

    /** Message header fields whose values are expressions that are evaluated. */
    private final MultiValueMap<String, Expression> headers =
            new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /** A form to include in the request, whose values are exchange-scoped expressions that are evaluated. */
    private final MultiValueMap<String, Expression> form =
            new MultiValueMap<String, Expression>(new CaseInsensitiveMap<List<Expression>>());

    /**
     * Builds a new {@link StaticRequestFilter} that will uses the given HTTP method on the resource.
     *
     * @param method
     *         The HTTP method to be performed on the resource
     */
    public StaticRequestFilter(final String method) {
        this.method = method;
    }

    /**
     * Sets the target URI as an expression to allow dynamic URI construction.
     *
     * @param uri
     *         target URI expression
     */
    public void setUri(final Expression uri) {
        this.uri = uri;
    }

    /**
     * Sets the new request message's version.
     *
     * @param version
     *         Protocol version (e.g. {@code "HTTP/1.1"}).
     */
    public void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Adds a new header value using the given {@code key} with the given {@link Expression}. As headers are
     * multi-valued objects, it's perfectly legal to call this method multiple times with the same key.
     *
     * @param key
     *         Header name
     * @param value
     *         {@link Expression} that represents the value of the new header
     * @return this object for fluent usage
     */
    public StaticRequestFilter addHeaderValue(final String key, final Expression value) {
        headers.add(key, value);
        return this;
    }

    /**
     * Adds a new form parameter using the given {@code key} with the given {@link Expression}. As form parameters are
     * multi-valued objects, it's perfectly legal to call this method multiple times with the same key.
     *
     * @param name
     *         Form parameter name
     * @param value
     *         {@link Expression} that represents the value of the parameter
     * @return this object for fluent usage
     */
    public StaticRequestFilter addFormParameter(final String name, final Expression value) {
        form.add(name, value);
        return this;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        Request request = new Request();
        request.method = this.method;
        String value = this.uri.eval(exchange, String.class);
        if (value != null) {
            try {
                request.uri = new URI(value);
            } catch (URISyntaxException e) {
                throw logger.debug(new HandlerException("The URI " + value + " was not valid, " + e.getMessage(), e));
            }
        } else {
            throw logger.debug(new HandlerException("The URI expression evaluated to null"));
        }
        if (this.version != null) {
            // default in Message class
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
        if (this.form != null && !this.form.isEmpty()) {
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
        next.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes a request filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            StaticRequestFilter filter = new StaticRequestFilter(config.get("method").required().asString());
            filter.setUri(asExpression(config.get("uri")));
            filter.setVersion(config.get("version").asString());

            JsonValue headers = config.get("headers").expect(Map.class);
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonValue value : headers.get(key).required().expect(List.class)) {
                        filter.addHeaderValue(key, asExpression(value.required()));
                    }
                }
            }
            JsonValue form = config.get("form").expect(Map.class);
            if (form != null) {
                for (String key : form.keys()) {
                    for (JsonValue value : form.get(key).required().expect(List.class)) {
                        filter.addFormParameter(key, asExpression(value.required()));
                    }
                }
            }
            return filter;
        }
    }
}
