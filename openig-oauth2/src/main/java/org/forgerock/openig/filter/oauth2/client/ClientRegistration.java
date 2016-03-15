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
package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.forgerock.http.protocol.Status.BAD_REQUEST;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.authz.modules.oauth2.OAuth2Error.bestEffortResourceServerError;
import static org.forgerock.authz.modules.oauth2.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.getJsonContent;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluate;
import static org.forgerock.openig.util.JsonValues.firstOf;
import static org.forgerock.util.Utils.closeSilently;

import java.nio.charset.Charset;
import java.util.List;

import org.forgerock.authz.modules.oauth2.OAuth2Error;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.http.Responses;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * A configuration for an OpenID Connect Provider. Options:
 *
 * <pre>
 * {@code
 * {
 *   "clientId"                     : expression,       [REQUIRED]
 *   "clientSecret"                 : expression,       [REQUIRED]
 *   "issuer"                       : String / Issuer   [REQUIRED - the issuer name, or its inlined declaration,
 *   "scopes"                       : [ expressions ],  [OPTIONAL - specific scopes to use for this client
 *                                                                  registration. ]
 *   "registrationHandler"          : handler           [OPTIONAL - by default it uses the 'ClientHandler'
 *                                                                  provided in heap.]
 *   "tokenEndpointUseBasicAuth"    : boolean           [OPTIONAL - default is true, use Basic Authentication.]
 * }
 * }
 * </pre>
 *
 * Example of use:
 *
 * <pre>
 * {@code
 * {
 *     "name": "MyClientRegistration",
 *     "type": "ClientRegistration",
 *     "config": {
 *         "clientId": "OpenIG",
 *         "clientSecret": "password",
 *         "scopes": [
 *             "openid",
 *             "profile"
 *         ],
 *         "issuer": "OpenAM"
 *     }
 * }
 * }
 * </pre>
 *
 * or, with inlined Issuer declaration:
 *
 * <pre>
 * {@code
 * {
 *     "name": "MyClientRegistration",
 *     "type": "ClientRegistration",
 *     "config": {
 *         "clientId": "OpenIG",
 *         "clientSecret": "password",
 *         "scopes": [
 *             "openid",
 *             "profile"
 *         ],
           "tokenEndpointUseBasicAuth": true,
 *         "issuer": {
 *             "name": "myIssuer",
 *             "type": "Issuer",
 *             "config": {
 *                 "wellKnownEndpoint": "http://server.com:8090/openam/oauth2/.well-known/openid-configuration"
 *             }
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @see <a
 *      href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">
 *      OpenID Connect Dynamic Client Registration 1.0 </a>
 */
public final class ClientRegistration {
    /** The key used to store this client registration in the context. */
    static final String CLIENT_REG_KEY = "registration";

    private final String name;
    private final String clientId;
    private final String clientSecret;
    private final Issuer issuer;
    private final List<String> scopes;
    private boolean tokenEndpointUseBasicAuth;
    private final Handler registrationHandler;

    /**
     * Creates a Client Registration.
     *
     * @param name
     *            The name of this client registration. Can be {@code null}. If
     *            it is {@code null} the name is extracted from the
     *            configuration.
     * @param config
     *            The configuration of the client registration.
     * @param issuer
     *            The {@link Issuer} of this Client.
     * @param registrationHandler
     *            The handler used to send request to the AS.
     */
    public ClientRegistration(final String name,
                              final JsonValue config,
                              final Issuer issuer,
                              final Handler registrationHandler) {
        this.name = name != null
                    ? name
                    : firstOf(config, "client_name", "client_id").asString();
        this.clientId = firstOf(config, "clientId", "client_id").required().asString();
        this.clientSecret = firstOf(config, "clientSecret", "client_secret").required().asString();
        this.scopes = config.get("scopes").defaultTo(emptyList()).required().asList(String.class);
        if (config.isDefined("token_endpoint_auth_method")
                && config.get("token_endpoint_auth_method").asString().equals("client_secret_post")) {
            this.tokenEndpointUseBasicAuth = false;
        } else {
            this.tokenEndpointUseBasicAuth = config.get("tokenEndpointUseBasicAuth").defaultTo(true).asBoolean();
        }
        this.issuer = issuer;
        this.registrationHandler = registrationHandler;
    }

    /**
     * Returns the name of this client registration.
     *
     * @return the name of this client registration.
     */
    public String getName() {
        return name;
    }

    /**
     * Exchanges the authorization code for an access token and optional ID
     * token, and then update the session state.
     *
     * @param context
     *            The current context.
     * @param code
     *            The authorization code.
     * @param callbackUri
     *            The callback URI.
     * @return A promise completed with either the json content of the response if status return code of the
     *         response is 200 OK or with an OAuth2ErrorException in case of errors.
     */
    public Promise<JsonValue, OAuth2ErrorException> getAccessToken(final Context context,
                                                                   final String code,
                                                                   final String callbackUri) {
        final Request request = createRequestForAccessToken(code, callbackUri);
        return httpRequestToAuthorizationServer(context, request)
                .then(new Function<Response, JsonValue, OAuth2ErrorException>() {
                    @Override
                    public JsonValue apply(Response response) throws OAuth2ErrorException {
                        checkResponseStatus(response, false);
                        return getJsonContent(response);
                    }
                },
                      Responses.<JsonValue, OAuth2ErrorException>noopExceptionFunction());
    }

    /**
     * Returns the client ID of this client registration.
     *
     * @return the client ID.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the {@link Issuer} for this client registration.
     *
     * @return the {@link Issuer} for this client registration.
     */
    public Issuer getIssuer() {
        return issuer;
    }

    /**
     * Refreshes the actual access token, making a refresh request to the token
     * end-point.
     *
     * @param context
     *            The current context.
     * @param session
     *            The current session.
     * @return A promise completed either with the JSON content of the response if status return code of the
     *         response is 200 OK,  or with an OAuth2ErrorException if an error occurs when contacting the authorization
     *         server or if the returned response status code is different than 200 OK.
     */
    public Promise<JsonValue, OAuth2ErrorException> refreshAccessToken(final Context context,
                                                                       final OAuth2Session session) {

        final Request request = createRequestForTokenRefresh(session);
        return httpRequestToAuthorizationServer(context, request)
                .then(new Function<Response, JsonValue, OAuth2ErrorException>() {
                    @Override
                    public JsonValue apply(Response response) throws OAuth2ErrorException {
                        checkResponseStatus(response, true);
                        return getJsonContent(response);
                    }
                },
                      Responses.<JsonValue, OAuth2ErrorException>noopExceptionFunction());
    }

    /**
     * Returns the list of scopes of this client registration.
     *
     * @return the the list of scopes of this client registration.
     */
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Returns a Promise completed either with the json value of the user info obtained from the authorization
     * server if the response from the authorization server has a status code of 200, or with an exception, meaning the
     * access token may have expired.
     *
     * @param context
     *            The current context.
     * @param session
     *            The current session to use.
     * @return A promise completed either with a JsonValue containing the requested user info, or with an
     *         OAuth2ErrorException if an error occurs when contacting the authorization server or if the returned
     *         response status code is different than 200 OK (That may signify that the access token has expired).
     */
    public Promise<JsonValue, OAuth2ErrorException> getUserInfo(final Context context, final OAuth2Session session) {
        final Request request = createRequestForUserInfo(session.getAccessToken());
        return httpRequestToAuthorizationServer(context, request)
                .then(new Function<Response, JsonValue, OAuth2ErrorException>() {
                    @Override
                    public JsonValue apply(Response response) throws OAuth2ErrorException {
                        if (!Status.OK.equals(response.getStatus())) {
                            // The access token may have expired. Trigger an exception,
                            // catch it and react later.
                            final OAuth2BearerWWWAuthenticateHeader header =
                                    OAuth2BearerWWWAuthenticateHeader.valueOf(response);
                            final OAuth2Error error = header.getOAuth2Error();
                            final OAuth2Error bestEffort = bestEffortResourceServerError(response.getStatus(), error);

                            closeSilently(response);

                            throw new OAuth2ErrorException(bestEffort);
                        }
                        return getJsonContent(response);
                    }
                },
                      Responses.<JsonValue, OAuth2ErrorException>noopExceptionFunction());
    }

    /**
     * Sets the authentication method the token end-point should use.
     * {@code true} for 'client_secret_basic', {@code false} for
     * 'client_secret_post' (not recommended).
     *
     * @param useBasicAuth
     *            {@code true} if the token end-point should use Basic
     *            authentication, {@code false} if it should use client secret
     *            POST.
     * @return This client registration.
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-2.3.1">RFC 6749, Section 2.3.1</a>
     */
    public ClientRegistration setTokenEndpointUseBasicAuth(final boolean useBasicAuth) {
        this.tokenEndpointUseBasicAuth = useBasicAuth;
        return this;
    }

    private Request createRequestForAccessToken(final String code,
                                                final String callbackUri) {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(issuer.getTokenEndpoint());
        final Form form = new Form();
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", callbackUri);
        form.add("code", code);
        addClientIdAndSecret(request, form);
        form.toRequestEntity(request);
        return request;
    }

    private Request createRequestForTokenRefresh(final OAuth2Session session) {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(issuer.getTokenEndpoint());
        final Form form = new Form();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", session.getRefreshToken());
        addClientIdAndSecret(request, form);
        form.toRequestEntity(request);
        return request;
    }

    private Request createRequestForUserInfo(final String accessToken) {
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(issuer.getUserInfoEndpoint());
        request.getHeaders().add("Authorization", "Bearer " + accessToken);
        return request;
    }

    private void addClientIdAndSecret(final Request request,
                                      final Form form) {
        final String user = getClientId();
        final String pass = getClientSecret();
        if (!tokenEndpointUseBasicAuth) {
            form.add("client_id", user);
            form.add("client_secret", pass);
        } else {
            final String userpass = Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset()));
            request.getHeaders().add("Authorization", "Basic " + userpass);
        }
    }

    private String getClientSecret() {
        return clientSecret;
    }

    private Promise<Response, NeverThrowsException> httpRequestToAuthorizationServer(final Context context,
                                                                                     final Request request) {
        return registrationHandler.handle(context, request);
    }

    private void checkResponseStatus(final Response response,
                                     final boolean isRefreshToken) throws OAuth2ErrorException {
        final Status status = response.getStatus();
        if (!OK.equals(status)) {
            if (BAD_REQUEST.equals(status) || UNAUTHORIZED.equals(status)) {
                final JsonValue errorJson = getJsonContent(response);
                throw new OAuth2ErrorException(OAuth2Error.valueOfJsonContent(errorJson.asMap()));
            } else {
                final String errorMessage = format("Unable to %s access token [status=%d]",
                                                   isRefreshToken ? "refresh" : "exchange",
                                                   status.getCode());
                throw new OAuth2ErrorException(E_SERVER_ERROR, errorMessage);
            }
        }
    }

    /** Creates and initializes a Client Registration object in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            final Handler registrationHandler = heap.resolve(config.get("registrationHandler")
                                                                   .defaultTo(CLIENT_HANDLER_HEAP_KEY),
                                                             Handler.class);
            final Issuer issuer = heap.resolve(config.get("issuer"), Issuer.class);
            return new ClientRegistration(this.name,
                                          evaluate(config, logger),
                                          issuer,
                                          registrationHandler);
        }
    }
}
