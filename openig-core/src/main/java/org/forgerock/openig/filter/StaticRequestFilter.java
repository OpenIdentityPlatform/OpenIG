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

import static java.lang.String.format;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.util.CaseInsensitiveMap;
import org.forgerock.http.util.MultiValueMap;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Responses;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Creates a new request with in the exchange object. It will replace any
 * request that may already be present in the exchange. The request can include
 * a form, specified in the {@code form} field, which is included in an entity
 * encoded in {@code application/x-www-form-urlencoded} format if request method
 * is {@code POST}, or otherwise as (additional) query parameters in the URI.
 *
 * <pre>
 * {@code
 * {
 *      "method"                     : string            [REQUIRED]
 *      "uri"                        : expression        [REQUIRED]
 *      "entity"                     : expression        [OPTIONAL - cannot be used simultaneously
 *                                                                   with a form in POST mode* ]
 *      "form"                       : object            [OPTIONAL]
 *      "headers"                    : object            [OPTIONAL]
 *      "version"                    : string            [OPTIONAL]
 * }
 * }
 * </pre>
 * <p>
 * *Nota: When method is set to POST, the entity and the form CANNOT be used
 * together in the heaplet because they both determine the request entity. They
 * still can used programmatically together but the form will override any
 * entity content.
 * <p>
 *
 * Example of use:
 *
 * <pre>
 * {@code
 * {
 *      "name": "customRequestFilter",
 *      "type": "StaticRequestFilter",
 *      "config": {
 *          "method": "POST",
 *          "uri": "http://10.10.0.2:8080/wp-login.php",
 *          "entity": "{\"auth\":{\"passwordCredentials\":
 *                     {\"username\":\"${exchange.attributes.username}\",
 *                      \"password\":\"${exchange.attributes.password}\"}}}"
 *          "headers": {
 *              "Warning": [ "199 Miscellaneous warning" ]
 *          }
 *      }
 * }
 * }
 * </pre>
 */
public class StaticRequestFilter extends GenericHeapObject implements Filter {

    /** The message entity expression. */
    private Expression<String> entity;

    /** The HTTP method to be performed on the resource. */
    private final String method;

    /** URI as an expression to allow dynamic URI construction. */
    private Expression<String> uri;

    /** Protocol version (e.g. {@code "HTTP/1.1"}). */
    private String version;

    /** Message header fields whose values are expressions that are evaluated. */
    private final MultiValueMap<String, Expression<String>> headers =
            new MultiValueMap<>(new CaseInsensitiveMap<List<Expression<String>>>());

    /** A form to include in the request, whose values are exchange-scoped expressions that are evaluated. */
    private final MultiValueMap<String, Expression<String>> form =
            new MultiValueMap<>(new CaseInsensitiveMap<List<Expression<String>>>());

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
     * Sets the message entity expression.
     *
     * @param entity
     *            The message entity expression.
     */
    public void setEntity(final Expression<String> entity) {
        this.entity = entity;
    }

    /**
     * Sets the target URI as an expression to allow dynamic URI construction.
     *
     * @param uri
     *         target URI expression
     */
    public void setUri(final Expression<String> uri) {
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
    public StaticRequestFilter addHeaderValue(final String key, final Expression<String> value) {
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
    public StaticRequestFilter addFormParameter(final String name, final Expression<String> value) {
        form.add(name, value);
        return this;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        Exchange exchange = context.asContext(Exchange.class);
        Bindings bindings = bindings(exchange, request);

        Request newRequest = new Request();
        newRequest.setMethod(this.method);
        String value = this.uri.eval(bindings);
        if (value != null) {
            try {
                newRequest.setUri(value);
            } catch (URISyntaxException e) {
                logger.debug(e);
                String message = format("The URI %s was not valid", value);
                return Promises.newResultPromise(Responses.newInternalServerError(message, e));
            }
        } else {
            String message = format("The URI expression '%s' could not be resolved", uri.toString());
            logger.debug(message);
            return Promises.newResultPromise(Responses.newInternalServerError(message));
        }

        if (entity != null) {
            newRequest.setEntity(entity.eval(bindings));
        }

        if (this.version != null) {
            // default in Message class
            newRequest.setVersion(version);
        }
        for (String key : this.headers.keySet()) {
            for (Expression<String> expression : this.headers.get(key)) {
                String eval = expression.eval(bindings);
                if (eval != null) {
                    newRequest.getHeaders().add(key, eval);
                }
            }
        }
        if (this.form != null && !this.form.isEmpty()) {
            Form f = new Form();
            for (String key : this.form.keySet()) {
                for (Expression<String> expression : this.form.get(key)) {
                    String eval = expression.eval(bindings);
                    if (eval != null) {
                        f.add(key, eval);
                    }
                }
            }
            if ("POST".equals(newRequest.getMethod())) {
                f.toRequestEntity(newRequest);
            } else {
                f.appendRequestQuery(newRequest);
            }
        }
        return next.handle(context, newRequest);
        // Note Can't restore in promise-land because I can't change the reference to the given request parameter
    }

    /** Creates and initializes a request filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final String method = config.get("method").required().asString();
            StaticRequestFilter filter = new StaticRequestFilter(method);
            filter.setUri(asExpression(config.get("uri").required(), String.class));
            filter.setVersion(config.get("version").asString());
            if (config.isDefined("entity")
                    && config.isDefined("form")
                    && "POST".equals(method)) {
                throw new HeapException("Invalid configuration. When \"method\": \"POST\", \"form\" and \"entity\" "
                        + "settings are mutually exclusive because they both determine the request entity.");
            }
            filter.setEntity(asExpression(config.get("entity"), String.class));

            JsonValue headers = config.get("headers").expect(Map.class);
            if (headers != null) {
                for (String key : headers.keys()) {
                    for (JsonValue value : headers.get(key).required().expect(List.class)) {
                        filter.addHeaderValue(key, asExpression(value.required(), String.class));
                    }
                }
            }
            JsonValue form = config.get("form").expect(Map.class);
            if (form != null) {
                for (String key : form.keys()) {
                    for (JsonValue value : form.get(key).required().expect(List.class)) {
                        filter.addFormParameter(key, asExpression(value.required(), String.class));
                    }
                }
            }
            return filter;
        }
    }
}
