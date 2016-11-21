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

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.openig.util.JsonValues.slashEnded;
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
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>The {@literal instance} attribute is the name of an STS instance: a pre-configured transformation available
 * under a specific REST endpoint.
 *
 * <p>The {@literal amHandler} attribute is a reference to a {@link Handler} heap object. That handler will be used
 * for all REST calls to OpenAM (as opposed to the {@code next} Handler of the filter method that is dedicated to
 * continue the execution flow through the chain).
 *
 * <p>After transformation, the returned {@literal issued_token} (at the moment it is a {@code String} that contains
 * the XML of the generated SAML assertions), is made available in the {@link StsContext} for downstream handlers.
 *
 * <p>If errors are happening during the token transformation, the error response is returned as-is to the caller,
 * and informative messages are being logged for the administrator.
 */
public class TokenTransformationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TokenTransformationFilter.class);

    private final Handler handler;
    private final URI endpoint;
    private final Expression<String> idToken;

    /**
     * Constructs a new TokenTransformationFilter transforming the OpenID Connect id_token from {@code idToken}
     * into a SAML 2.0 Assertions structure (into {@code target}).
     *
     * @param handler pipeline used to send the STS transformation request
     * @param endpoint Fully qualified URI of the STS instance (including the {@literal _action=translate} query string)
     * @param idToken Expression for reading OpenID Connect id_token (expects a {@code String})
     */
    public TokenTransformationFilter(final Handler handler,
                                     final URI endpoint,
                                     final Expression<String> idToken) {
        this.handler = checkNotNull(handler);
        this.endpoint = checkNotNull(endpoint);
        this.idToken = checkNotNull(idToken);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        final String resolvedIdToken = idToken.eval(bindings(context, request));
        if (resolvedIdToken == null) {
            logger.error("OpenID Connect id_token expression ({}) has evaluated to null", idToken);
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
                        logger.error("Server side error ({}, {}) while transforming id_token:{}",
                                     response.getStatus(),
                                     json.get("reason"),
                                     json.get("message"));
                        return newResponsePromise(new Response(Status.BAD_GATEWAY));
                    }

                    String token = (String) json.get("issued_token");
                    if (token == null) {
                        // Unlikely to happen, since this is an OK response
                        logger.error("STS issued_token is null");
                        return newResponsePromise(newInternalServerError());
                    }

                    // Forward the initial request
                    return next.handle(new StsContext(context, token), request);
                } catch (IOException e) {
                    logger.error("Can't get JSON back from {}", endpoint, e);
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
            Handler amHandler = config.get("amHandler").defaultTo(FORGEROCK_CLIENT_HANDLER_HEAP_KEY)
                                                       .as(requiredHeapObject(heap, Handler.class));
            URI openamUri = config.get("openamUri").as(evaluatedWithHeapProperties())
                                                   .required()
                                                   .as(slashEnded())
                                                   .as(uri());
            String realm = config.get("realm").as(evaluatedWithHeapProperties()).defaultTo("/").asString();
            String ssoTokenHeader = config.get("ssoTokenHeader").as(evaluatedWithHeapProperties()).asString();
            String username = config.get("username").required().as(evaluatedWithHeapProperties()).asString();
            String password = config.get("password").required().as(evaluatedWithHeapProperties()).asString();
            HeadlessAuthenticationFilter headlessAuthenticationFilter = new HeadlessAuthenticationFilter(amHandler,
                                                                                                         openamUri,
                                                                                                         realm,
                                                                                                         ssoTokenHeader,
                                                                                                         username,
                                                                                                         password);

            Expression<String> idToken = config.get("idToken").required().as(expression(String.class));

            String instance = config.get("instance").as(evaluatedWithHeapProperties()).required().asString();

            return new TokenTransformationFilter(Handlers.chainOf(amHandler, headlessAuthenticationFilter),
                                                 transformationEndpoint(openamUri, realm, instance),
                                                 idToken);
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
