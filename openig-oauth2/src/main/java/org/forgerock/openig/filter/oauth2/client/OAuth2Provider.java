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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.*;
import static java.util.Collections.emptyList;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.*;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.*;
import static org.forgerock.openig.http.HttpClient.*;
import static org.forgerock.openig.util.JsonValues.*;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Function;

/**
 * A configuration for an OAuth 2.0 authorization server or OpenID Connect Provider.
 * The OAuth2Provider handles all interactions with the authorization server.
 * Options:
 *
 * <pre>
 * {
 *   "clientId"                     : expression,      [REQUIRED]
 *   "clientSecret"                 : expression,      [REQUIRED]
 *   "wellKnownConfiguration"       : String,          [OPTIONAL - if authorize and token end-points are specified]
 *   "authorizeEndpoint"            : uriExpression,   [REQUIRED - if no well-known configuration]
 *   "tokenEndpoint"                : uriExpression,   [REQUIRED - if no well-known configuration]
 *   "userInfoEndpoint"             : uriExpression,   [OPTIONAL - if no well-known configuration,
 *                                                                 default is no user info]
 *   "scopes"                       : [ expressions ], [OPTIONAL - overrides global scopes]
 *   "providerHandler"              : handler          [OPTIONAL - default is using a new ClientHandler
 *                                                                 wrapping the default HttpClient.]
 * }
 * </pre>
 *
 * For example:
 *
 * <pre>
 * {
 *     "name": "openam",
 *     "type": "OAuth2Provider",
 *     "config": {
 *          "clientId": "OpenIG",
 *          "clientSecret": "password",
 *          "authorizeEndpoint": "http://www.example.com:8081/openam/oauth2/authorize",
 *          "tokenEndpoint": "http://www.example.com:8081/openam/oauth2/access_token",
 *          "userInfoEndpoint": "http://www.example.com:8081/openam/oauth2/userinfo"
 *     }
 * }
 * </pre>
 */
public class OAuth2Provider {
    private final String name;
    private Expression<String> clientId;
    private Expression<String> clientSecret;
    private List<Expression<String>> scopes;
    private Expression<String> authorizeEndpoint;
    private Expression<String> tokenEndpoint;
    private Expression<String> userInfoEndpoint;
    private final boolean tokenEndpointUseBasicAuth = false; // Do we want to make this configurable?
    private Handler providerHandler;

    /**
     * Creates a new provider having the specified name. The returned provider
     * should be configured using the setters before being used.
     *
     * @param name
     *            The provider name.
     */
    public OAuth2Provider(final String name) {
        this.name = name;
    }

    /**
     * Sets the expression which will be used for obtaining the authorization
     * server's authorize end-point. This configuration parameter is required.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the
     *            authorization server's authorize end-point.
     * @return This provider.
     */
    public OAuth2Provider setAuthorizeEndpoint(final Expression<String> endpoint) {
        this.authorizeEndpoint = endpoint;
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the OAuth 2 client
     * ID. This configuration parameter is required.
     *
     * @param clientId
     *            The expression which will be used for obtaining the OAuth 2
     *            client ID.
     * @return This provider.
     */
    public OAuth2Provider setClientId(final Expression<String> clientId) {
        this.clientId = clientId;
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the OAuth 2 client
     * secret. This configuration parameter is required.
     *
     * @param clientSecret
     *            The expression which will be used for obtaining the OAuth 2
     *            client secret.
     * @return This provider.
     */
    public OAuth2Provider setClientSecret(final Expression<String> clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    /**
     * Sets the expressions which will be used for obtaining the OAuth 2 scopes
     * for this provider. This configuration parameter is optional and defaults
     * to the set of scopes defined for the client filter.
     *
     * @param scopes
     *            The expressions which will be used for obtaining the OAuth 2
     *            scopes.
     * @return This provider.
     */
    public OAuth2Provider setScopes(final List<Expression<String>> scopes) {
        this.scopes = scopes != null ? scopes : Collections.<Expression<String>> emptyList();
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the authorization
     * server's access token end-point. This configuration parameter is
     * required.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the
     *            authorization server's access token end-point.
     * @return This provider.
     */
    public OAuth2Provider setTokenEndpoint(final Expression<String> endpoint) {
        this.tokenEndpoint = endpoint;
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the authorization
     * server's OpenID Connect user info end-point. This configuration parameter
     * is optional.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the
     *            authorization server's OpenID Connect user info end-point.
     * @return This provider.
     */
    public OAuth2Provider setUserInfoEndpoint(final Expression<String> endpoint) {
        this.userInfoEndpoint = endpoint;
        return this;
    }

    /**
     * Extracts the different end-points from the .well-known URI, such as
     * authorization_endpoint, token_endpoint, userinfo_endpoint, etc.
     *
     * @param uri
     *            The .well-known configuration URI.
     * @return This provider.
     * @throws HeapException
     *             If an error occurs when trying to retrieve the end-points
     *             from the given URI.
     */
    public OAuth2Provider setWellKnownConfiguration(final URI uri) throws HeapException {
        Request request = new Request();
        request.setMethod("GET");
        request.setUri(uri);

        try {
            providerHandler.handle(new Exchange(), request)
                           .then(new Function<Response, Response, ResponseException>() {
                               @Override
                               public Response apply(final Response response) throws ResponseException {
                                   if (response.getStatus() != 200) {
                                       throw new ResponseException(
                                               "Unable to read well-known OpenID Configuration from '"
                                                       + uri + "'");
                                   }
                                   try {
                                       final JsonValue config = getJsonContent(response);
                                       setAuthorizeEndpoint(asExpression(config.get("authorization_endpoint")
                                                                               .required(), String.class));
                                       setTokenEndpoint(asExpression(config.get("token_endpoint").required(),
                                                                     String.class));
                                       setUserInfoEndpoint(asExpression(config.get("userinfo_endpoint"), String.class));
                                   } catch (OAuth2ErrorException e) {
                                       throw new ResponseException("Cannot read JSON", e);
                                   }
                                   return response;
                               }
                           }).getOrThrow();
        } catch (Exception e) {
            throw new HeapException("Unable to read well-known OpenID Configuration from '" + uri
                                            + "'", e);
        }
        return this;
    }

    /**
     * Sets the handler which will be used for communicating with the
     * authorization server.
     *
     * @param providerHandler
     *            The handler which will be used for communicating with the
     *            authorization server.
     */
    public void setProviderHandler(Handler providerHandler) {
        this.providerHandler = providerHandler;
    }

    private Request createRequestForAccessToken(final Exchange exchange, final String code,
            final String callbackUri) throws ResponseException {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(buildUri(exchange, tokenEndpoint));
        final Form form = new Form();
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", callbackUri);
        form.add("code", code);
        addClientIdAndSecret(exchange, request, form);
        form.toRequestEntity(request);
        return request;
    }

    Request createRequestForTokenRefresh(final Exchange exchange, final OAuth2Session session)
            throws ResponseException {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(buildUri(exchange, tokenEndpoint));
        final Form form = new Form();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", session.getRefreshToken());
        addClientIdAndSecret(exchange, request, form);
        form.toRequestEntity(request);
        return request;
    }

    private Request createRequestForUserInfo(final Exchange exchange, final String accessToken)
            throws ResponseException {
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(buildUri(exchange, userInfoEndpoint));
        request.getHeaders().add("Authorization", "Bearer " + accessToken);
        return request;
    }

    private Response httpRequestToAuthorizationServer(final Exchange exchange, final Request request)
            throws OAuth2ErrorException, ResponseException {
        try {
            return providerHandler.handle(exchange, request).getOrThrow();
        } catch (final InterruptedException e) {
            // FIXME Changed IOException to InterruptedException, not very sure about that
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                                           "Authorization failed because an error occurred while trying "
                                                   + "to contact the authorization server");
        }
    }

    URI getAuthorizeEndpoint(final Exchange exchange) throws ResponseException {
        return buildUri(exchange, authorizeEndpoint);
    }

    String getClientId(final Exchange exchange) throws ResponseException {
        final String result = clientId.eval(exchange);
        if (result == null) {
            throw new ResponseException(
                    format("The clientId expression '%s' could not be resolved", clientId.toString()));
        }
        return result;
    }

    String getName() {
        return name;
    }

    List<String> getScopes(final Exchange exchange) throws ResponseException {
        return OAuth2Utils.getScopes(exchange, scopes);
    }

    boolean hasUserInfoEndpoint() {
        return userInfoEndpoint != null;
    }

    /**
     * Exchanges the authorization code for an access token and optional ID
     * token, and then update the session state.
     *
     * @param exchange
     *            The current exchange.
     * @param code
     *            The authorization code.
     * @param callbackUri
     *            The callback URI.
     * @return The json content of the response if status return code of the
     *         response is 200 OK. Otherwise, throw an OAuth2ErrorException.
     * @throws ResponseException
     *             If an exception occurs that prevents handling of the request
     *             or if the creation of the request for an access token fails.
     * @throws OAuth2ErrorException
     *             If an error occurs when contacting the authorization server
     *             or if the returned response status code is different than 200
     *             OK.
     */
    JsonValue getAccessToken(final Exchange exchange,
                             final String code,
                             final String callbackUri) throws ResponseException, OAuth2ErrorException {
        final Request request = createRequestForAccessToken(exchange, code, callbackUri);
        final Response response = httpRequestToAuthorizationServer(exchange, request);
        checkResponseStatus(response, false);
        return getJsonContent(response);
    }

    /**
     * Returns the refresh token from the authorization server.
     *
     * @param exchange
     *            The current exchange.
     * @param session
     *            The current session.
     * @return The json content of the response if status return code of the
     *         response is 200 OK. Otherwise, throw an OAuth2ErrorException.
     * @throws ResponseException
     *             If an exception occurs that prevents handling of the request
     *             or if the creation of the request for a refresh token fails.
     * @throws OAuth2ErrorException
     *             If an error occurs when contacting the authorization server
     *             or if the returned response status code is different than 200
     *             OK.
     */
    JsonValue getRefreshToken(final Exchange exchange,
                              final OAuth2Session session) throws ResponseException, OAuth2ErrorException {

        final Request request = createRequestForTokenRefresh(exchange, session);
        final Response response = httpRequestToAuthorizationServer(exchange, request);
        checkResponseStatus(response, true);
        return getJsonContent(response);
    }

    private void checkResponseStatus(final Response response,
                                     final boolean isRefreshToken) throws OAuth2ErrorException {
        if (response.getStatus() != 200) {
            if (response.getStatus() == 400 || response.getStatus() == 401) {
                final JsonValue errorJson = getJsonContent(response);
                throw new OAuth2ErrorException(OAuth2Error.valueOfJsonContent(errorJson.asMap()));
            } else {
                final String errorMessage =
                        format("Unable to %s access token [status=%d]", isRefreshToken ? "refresh" : "exchange",
                                response.getStatus());
                throw new OAuth2ErrorException(E_SERVER_ERROR, errorMessage);
            }
        }
    }

    /**
     * Returns the json value of the user info obtained from the authorization
     * server if the response from the authorization server has a status code of
     * 200. Otherwise, it throws an exception, meaning the access token may have
     * expired.
     *
     * @param exchange
     *            The current exchange.
     * @param session
     *            The current session to use.
     * @return A JsonValue containing the requested user info.
     * @throws ResponseException
     *             If an exception occurs that prevents handling of the request
     *             or if the creation of the request for getting user info
     *             fails.
     * @throws OAuth2ErrorException
     *             If an error occurs when contacting the authorization server
     *             or if the returned response status code is different than 200
     *             OK. May signify that the access token has expired.
     */
    JsonValue getUserInfo(final Exchange exchange,
                          final OAuth2Session session) throws ResponseException, OAuth2ErrorException  {
        final Request request = createRequestForUserInfo(exchange, session.getAccessToken());
        final Response response = httpRequestToAuthorizationServer(exchange, request);
        if (response.getStatus() != 200) {
            /*
             * The access token may have expired. Trigger an exception,
             * catch it and react later.
             */
            final OAuth2BearerWWWAuthenticateHeader header = OAuth2BearerWWWAuthenticateHeader.valueOf(response);
            final OAuth2Error error = header.getOAuth2Error();
            final OAuth2Error bestEffort = OAuth2Error.bestEffortResourceServerError(response.getStatus(), error);
            throw new OAuth2ErrorException(bestEffort);
        }
        return getJsonContent(response);
    }

    private void addClientIdAndSecret(final Exchange exchange, final Request request,
            final Form form) throws ResponseException {
        final String user = getClientId(exchange);
        final String pass = getClientSecret(exchange);
        if (!tokenEndpointUseBasicAuth) {
            form.add("client_id", user);
            form.add("client_secret", pass);
        } else {
            final String userpass =
                    Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset()));
            request.getHeaders().add("Authorization", "Basic " + userpass);
        }
    }

    private String getClientSecret(final Exchange exchange) throws ResponseException {
        final String result = clientSecret.eval(exchange);
        if (result == null) {
            throw new ResponseException(
                    format("The clientSecret expression '%s' could not be resolved", clientSecret.toString()));
        }
        return result;
    }

    /**
     * Creates and initializes an OAuth2Provider object in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {

            final OAuth2Provider provider = new OAuth2Provider(this.name);
            provider.setClientId(asExpression(config.get("clientId").required(), String.class));
            provider.setClientSecret(asExpression(config.get("clientSecret").required(), String.class));
            provider.setScopes(config.get("scopes").defaultTo(emptyList()).asList(ofExpression()));
            Handler providerHandler = null;
            if (config.isDefined("providerHandler")) {
                providerHandler = heap.resolve(config.get("providerHandler"), Handler.class);
            } else {
                final HttpClient httpClient =
                        heap.resolve(config.get("httpClient").defaultTo(HTTP_CLIENT_HEAP_KEY), HttpClient.class);
                providerHandler = new ClientHandler(httpClient);
            }
            provider.setProviderHandler(providerHandler);
            final JsonValue knownConfiguration = config.get("wellKnownConfiguration");
            if (!knownConfiguration.isNull()) {
                provider.setWellKnownConfiguration(knownConfiguration.asURI());
            } else {
                provider.setAuthorizeEndpoint(asExpression(config.get("authorizeEndpoint").required(), String.class));
                provider.setTokenEndpoint(asExpression(config.get("tokenEndpoint").required(), String.class));
                provider.setUserInfoEndpoint(asExpression(config.get("userInfoEndpoint"), String.class));
            }
            return provider;
        }
    }
}
