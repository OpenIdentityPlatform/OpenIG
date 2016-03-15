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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static java.lang.String.format;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.JsonValues.evaluateJsonStaticExpression;
import static org.forgerock.openig.util.StringUtil.trailingSlash;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * A {@link TokenTransformationFilter} is responsible to transform a token issued by OpenAM
 * into a token of another type.
 *
 * <p>Currently only the OpenID Connect id_token to SAML 2.0 Token (Assertions) is supported, {@literal BEARER} mode.
 *
 * <pre>
 *     {@code {
 *         "type": "TokenTransformationFilter",
 *         "config": {
 *             "openamUri": "https://openam.example.com/openam/",
 *             "realm": "/my-realm",
 *             "username": "${attributes.username}",
 *             "password": "${attributes.password}",
 *             "idToken": "${attributes.id_token}",
 *             "target": "${attributes.saml_assertions}",
 *             "instance": "oidc-to-saml",
 *             "amHandler": "#Handler"
 *         }
 *     }
 *     }
 * </pre>
 *
 * <p>The {@literal openamUri} attribute is the OpenAM base URI against which authentication
 * and STS requests will be issued.
 *
 * <p>The {@literal realm} attribute is the OpenAM realm that contains both the subject
 * (described through {@literal username} and {@literal password} attributes) and the STS
 * instance (described with {@literal instance}).
 *
 * <p>The {@literal idToken} attribute is an {@link Expression} specifying where to get the JWT id_token.
 * Note that the referenced value has to be a {@code String} (the JWT encoded value).
 *
 * <p>The {@literal target} attribute is an {@link Expression} specifying where to place the
 * result of the transformation. Note that the pointed location will contains a {@code String}.
 *
 * <p>The {@literal instance} attribute is the name of an STS instance: a pre-configured transformation available
 * under a specific REST endpoint.
 *
 * <p>The {@literal amHandler} attribute is a reference to a {@link Handler} heap object. That handler will be used
 * for all REST calls to OpenAM (as opposed to the {@code next} Handler of the filter method that is dedicated to
 * continue the execution flow through the chain).
 *
 * <p>If errors are happening during the token transformation, the error response is returned as-is to the caller,
 * and informative messages are being logged for the administrator.
 */
public class TokenTransformationFilter extends GenericHeapObject implements Filter {

    private final Handler handler;
    private final URI endpoint;
    private final Expression<String> idToken;
    private final Expression<String> target;

    /**
     * Constructs a new TokenTransformationFilter transforming the OpenID Connect id_token from {@code idToken}
     * into a SAML 2.0 Assertions structure (into {@code target}).
     *
     * @param handler pipeline used to send the STS transformation request
     * @param endpoint Fully qualified URI of the STS instance (including the {@literal _action=translate} query string)
     * @param idToken Expression for reading OpenID Connect id_token (expects a {@code String})
     * @param target Expression for writing SAML 2.0 token (expects a {@code String})
     */
    public TokenTransformationFilter(final Handler handler,
                                     final URI endpoint,
                                     final Expression<String> idToken,
                                     final Expression<String> target) {
        this.handler = checkNotNull(handler);
        this.endpoint = checkNotNull(endpoint);
        this.idToken = checkNotNull(idToken);
        this.target = checkNotNull(target);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        final String resolvedIdToken = idToken.eval(bindings(context, request));
        if (resolvedIdToken == null) {
            logger.error(format("OpenID Connect id_token expression (%s) has evaluated to null", idToken));
            return newResponsePromise(newInternalServerError());
        }

        return handler.handle(context, transformationRequest(resolvedIdToken))
                      .thenAsync(processIssuedToken(context, request, next));
    }

    private AsyncFunction<Response, Response, NeverThrowsException> processIssuedToken(final Context context,
                                                                                       final Request request,
                                                                                       final Handler next) {
        return new AsyncFunction<Response, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final Response response) {
                try {
                    Map<String, Object> json = parseJsonObject(response);
                    if (response.getStatus() != Status.OK) {
                        logger.error(format("Server side error (%s, %s) while transforming id_token:%s",
                                            response.getStatus(),
                                            json.get("reason"),
                                            json.get("message")));
                        return newResponsePromise(new Response(Status.BAD_GATEWAY));
                    }

                    String token = (String) json.get("issued_token");
                    if (token == null) {
                        // Unlikely to happen, since this is an OK response
                        logger.error("STS issued_token is null");
                        return newResponsePromise(newInternalServerError());
                    }
                    target.set(bindings(context, request, response), token);

                    // Forward the initial request
                    return next.handle(context, request);
                } catch (IOException e) {
                    logger.error(format("Can't get JSON back from %s", endpoint));
                    logger.error(e);
                    return newResponsePromise(newInternalServerError(e));
                }
            }

            @SuppressWarnings("unchecked")
            private Map<String, Object> parseJsonObject(final Response response) throws IOException {
                return (Map<String, Object>) response.getEntity().getJson();
            }
        };
    }

    private Request transformationRequest(final String resolvedIdToken) {
        return new Request().setUri(endpoint)
                            .setMethod("POST")
                            .setEntity(transformation(resolvedIdToken));
    }

    private static Object transformation(String idToken) {
        return object(field("input_token_state", object(field("token_type", "OPENIDCONNECT"),
                                                        field("oidc_id_token", idToken))),
                      field("output_token_state", object(field("token_type", "SAML2"),
                                                         field("subject_confirmation", "BEARER"))));
    }

    /** Creates and initializes a token transformation filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            Handler amHandler = heap.resolve(config.get("amHandler").required(),
                                             Handler.class);
            URI baseUri = getOpenamBaseUri();
            String realm = config.get("realm").defaultTo("/").asString();
            String ssoTokenHeader = config.get("ssoTokenHeader").asString();
            Expression<String> username = asExpression(config.get("username").required(), String.class);
            Expression<String> password = asExpression(config.get("password").required(), String.class);
            SsoTokenFilter ssoTokenFilter = new SsoTokenFilter(amHandler,
                                                               baseUri,
                                                               realm,
                                                               ssoTokenHeader,
                                                               username,
                                                               password,
                                                               logger);

            Expression<String> idToken = asExpression(config.get("idToken").required(), String.class);
            Expression<String> target = asExpression(config.get("target").required(), String.class);

            String instance = evaluateJsonStaticExpression(config.get("instance").required()).asString();

            return new TokenTransformationFilter(Handlers.chainOf(amHandler, ssoTokenFilter),
                                                 transformationEndpoint(baseUri, realm, instance),
                                                 idToken,
                                                 target);
        }

        private URI getOpenamBaseUri() throws HeapException {
            String baseUri = config.get("openamUri").required().asString();
            try {
                return new URI(trailingSlash(baseUri));
            } catch (URISyntaxException e) {
                throw new HeapException(format("Cannot append trailing '/' on %s", baseUri), e);
            }
        }

        private static URI transformationEndpoint(final URI baseUri, final String realm, final String instance)
                throws HeapException {
            try {
                StringBuilder sb = new StringBuilder("rest-sts");
                if (!realm.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(realm);
                if (!realm.endsWith("/") && !instance.startsWith("/")) {
                    sb.append("/");
                }
                sb.append(instance);
                sb.append("?_action=translate");

                return baseUri.resolve(new URI(sb.toString()));
            } catch (URISyntaxException e) {
                throw new HeapException("Can't build STS endpoint URI", e);
            }
        }
    }
}
