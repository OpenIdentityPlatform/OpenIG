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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.*;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.*;
import static org.forgerock.openig.util.JsonValueUtil.*;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.common.JwtReconstruction;
import org.forgerock.json.jose.exceptions.JwtReconstructionException;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.util.encode.Base64;

/**
 * An OAuth 2.0 authorization server or OpenID Connect Provider.
 */
public class OAuth2Provider {
    private final JwtReconstruction jwtConstructor = new JwtReconstruction();
    private final String name;
    private Expression authorizeEndpoint;
    private Expression clientId;
    private Expression clientSecret;
    private List<Expression> scopes;
    private Expression tokenEndpoint;
    private Expression userInfoEndpoint;
    private final boolean tokenEndpointUseBasicAuth = false; // Do we want to make this configurable?

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
    public OAuth2Provider setAuthorizeEndpoint(final Expression endpoint) {
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
    public OAuth2Provider setClientId(final Expression clientId) {
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
    public OAuth2Provider setClientSecret(final Expression clientSecret) {
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
    public OAuth2Provider setScopes(final List<Expression> scopes) {
        this.scopes = scopes != null ? scopes : Collections.<Expression> emptyList();
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
    public OAuth2Provider setTokenEndpoint(final Expression endpoint) {
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
    public OAuth2Provider setUserInfoEndpoint(final Expression endpoint) {
        this.userInfoEndpoint = endpoint;
        return this;
    }

    /**
     * Configures this provider using the specified OpenID Connect Well Known
     * configuration.
     *
     * @param wellKnown
     *            The OpenID Connect provider's Well Known configuration.
     * @return This provider.
     */
    public OAuth2Provider setWellKnownConfiguration(final JsonValue wellKnown) {
        setAuthorizeEndpoint(asExpression(wellKnown.get("authorization_endpoint").required()));
        setTokenEndpoint(asExpression(wellKnown.get("token_endpoint").required()));
        setUserInfoEndpoint(asExpression(wellKnown.get("userinfo_endpoint")));
        return this;
    }

    Request createRequestForAccessToken(final Exchange exchange, final String code,
            final String callbackUri) throws HandlerException {
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
            throws HandlerException {
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

    Request createRequestForUserInfo(final Exchange exchange, final String accessToken)
            throws HandlerException {
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(buildUri(exchange, userInfoEndpoint));
        request.getHeaders().add("Authorization", "Bearer " + accessToken);
        return request;
    }

    /**
     * ID Token extraction is the responsibility of the provider because each
     * provider may have different crypto configuration.
     */
    SignedJwt extractIdToken(final JsonValue accessTokenResponse) throws OAuth2ErrorException {
        if (accessTokenResponse.isDefined("id_token")) {
            final String idToken = accessTokenResponse.get("id_token").asString();
            try {
                return jwtConstructor.reconstructJwt(idToken, SignedJwt.class);
            } catch (final JwtReconstructionException e) {
                throw new OAuth2ErrorException(E_SERVER_ERROR,
                        "Authorization call-back failed because the OpenID Connect ID token"
                                + "could not be decoded");
            }
        }
        return null;
    }

    URI getAuthorizeEndpoint(final Exchange exchange) throws HandlerException {
        return buildUri(exchange, authorizeEndpoint);
    }

    String getClientId(final Exchange exchange) throws HandlerException {
        final String result = clientId.eval(exchange, String.class);
        if (result == null) {
            throw new HandlerException("Unable to determine the clientId");
        }
        return result;
    }

    String getName() {
        return name;
    }

    List<String> getScopes(final Exchange exchange) throws HandlerException {
        return OAuth2Utils.getScopes(exchange, scopes);
    }

    boolean hasUserInfoEndpoint() {
        return userInfoEndpoint != null;
    }

    private void addClientIdAndSecret(final Exchange exchange, final Request request,
            final Form form) throws HandlerException {
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

    private String getClientSecret(final Exchange exchange) throws HandlerException {
        final String result = clientSecret.eval(exchange, String.class);
        if (result == null) {
            throw new HandlerException("Unable to determine the clientSecret");
        }
        return result;
    }
}
