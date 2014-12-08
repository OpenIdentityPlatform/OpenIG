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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_INVALID_REQUEST;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_SERVER_ERROR;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.common.JwtReconstruction;
import org.forgerock.json.jose.exceptions.JwtReconstructionException;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.util.time.TimeService;

/**
 * Represents the current state of a user's OAuth 2 authorization.
 */
final class OAuth2Session {
    private static final JwtReconstruction JWT_DECODER = new JwtReconstruction();

    static OAuth2Session stateNew(final TimeService time) {
        return new OAuth2Session(time, null, null, null, Collections.<String> emptyList(),
                new JsonValue(emptyMap()), null, null);
    }

    private static SignedJwt extractIdToken(final JsonValue accessTokenResponse)
            throws OAuth2ErrorException {
        if (accessTokenResponse.isDefined("id_token")) {
            final String idToken = accessTokenResponse.get("id_token").asString();
            try {
                return JWT_DECODER.reconstructJwt(idToken, SignedJwt.class);
            } catch (final JwtReconstructionException e) {
                throw new OAuth2ErrorException(E_SERVER_ERROR,
                        "Authorization call-back failed because the OpenID Connect ID token"
                                + "could not be decoded");
            }
        }
        return null;
    }

    private void putIfNotNullOrEmpty(final Map<String, Object> json, final String key,
            final Object value) {
        if (value == null) {
            return;
        } else if ((value instanceof Collection<?>) && ((Collection<?>) value).isEmpty()) {
            return;
        } else if ((value instanceof Map<?, ?>) && ((Map<?, ?>) value).isEmpty()) {
            return;
        } else {
            json.put(key, value);
        }
    }

    static OAuth2Session fromJson(TimeService time, JsonValue json) throws OAuth2ErrorException {
        try {
            final String providerName = json.get("pn").asString();
            final String clientEndpoint = json.get("ce").asString();
            final List<String> scopes = json.get("s").defaultTo(emptyList()).asList(String.class);
            final String authorizationRequestNonce = json.get("arn").asString();
            final JsonValue accessTokenResponse = json.get("atr").defaultTo(emptyMap());
            final Long expiresAt = json.get("ea").asLong();
            final SignedJwt idToken = extractIdToken(accessTokenResponse);
            return new OAuth2Session(time, providerName, clientEndpoint, authorizationRequestNonce,
                    scopes, accessTokenResponse, idToken, expiresAt);
        } catch (Exception e) {
            throw new OAuth2ErrorException(
                    E_INVALID_REQUEST,
                    "The request could not be authorized because the session content could not be parsed",
                    e);
        }
    }

    JsonValue toJson() {
        // Use short field names to save on space - cookies have a 4KB limit.
        final Map<String, Object> json = new LinkedHashMap<String, Object>();
        putIfNotNullOrEmpty(json, "pn", providerName);
        putIfNotNullOrEmpty(json, "ce", clientEndpoint);
        putIfNotNullOrEmpty(json, "s", scopes);
        putIfNotNullOrEmpty(json, "arn", authorizationRequestNonce);
        putIfNotNullOrEmpty(json, "atr", accessTokenResponse.getObject());
        putIfNotNullOrEmpty(json, "ea", expiresAt);
        return new JsonValue(json);
    }

    private final JsonValue accessTokenResponse;
    private final List<String> scopes;
    private final String authorizationRequestNonce;
    private final String clientEndpoint;
    private final Long expiresAt;
    private final SignedJwt idToken;
    private final String providerName;
    private final TimeService time;

    private OAuth2Session(final TimeService time, final String providerName,
            final String clientEndpoint, final String authorizationRequestNonce,
            final List<String> scopes, final JsonValue accessTokenResponse,
            final SignedJwt idToken, final Long expiresAt) {
        this.time = time;
        this.providerName = providerName;
        this.clientEndpoint = clientEndpoint;
        this.scopes = scopes;
        this.authorizationRequestNonce = authorizationRequestNonce;
        this.accessTokenResponse = accessTokenResponse;
        this.expiresAt = expiresAt;
        this.idToken = idToken;
    }

    String getAccessToken() {
        return accessTokenResponse.get("access_token").asString();
    }

    Map<String, Object> getAccessTokenResponse() {
        return accessTokenResponse.asMap();
    }

    String getAuthorizationRequestNonce() {
        return authorizationRequestNonce;
    }

    String getClientEndpoint() {
        return clientEndpoint;
    }

    Long getExpiresIn() {
        return expiresAt != null ? expiresAt - now() : null;
    }

    SignedJwt getIdToken() {
        return idToken;
    }

    String getProviderName() {
        return providerName;
    }

    String getRefreshToken() {
        return accessTokenResponse.get("refresh_token").asString();
    }

    List<String> getScopes() {
        return scopes;
    }

    String getTokenType() {
        return accessTokenResponse.get("token_type").asString();
    }

    boolean isAuthorized() {
        return getAccessToken() != null;
    }

    boolean isAuthorizing() {
        return authorizationRequestNonce != null;
    }

    OAuth2Session stateAuthorized(final JsonValue newAccessTokenResponse)
            throws OAuth2ErrorException {
        // Merge old token response with new.
        final Map<String, Object> mergedAccessTokenResponse =
                new LinkedHashMap<String, Object>(accessTokenResponse.asMap());
        mergedAccessTokenResponse.putAll(newAccessTokenResponse.asMap());
        JsonValue accessTokenResponse =
                new JsonValue(Collections.unmodifiableMap(mergedAccessTokenResponse));

        // Compute effective scopes.
        final JsonValue returnedScopes = newAccessTokenResponse.get("scope");
        final List<String> actualScopes;
        if (returnedScopes.isString()) {
            actualScopes = Arrays.asList(returnedScopes.asString().trim().split("\\s+"));
        } else {
            actualScopes = scopes;
        }

        // Compute expiration time from expires_in field.
        long expiresIn;
        JsonValue expires = newAccessTokenResponse.get("expires_in");
        if (expires.isString()) {
            expiresIn = Long.valueOf(expires.asString());
        } else if (expires.isNumber()) {
            expiresIn = expires.asNumber().longValue();
        } else {
            throw new OAuth2ErrorException(OAuth2Error.E_SERVER_ERROR,
                                           "'expire_in' field value is neither a Number nor a String");
        }
        final Long expiresAt = expiresIn + now();

        // Decode the ID token for OpenID Connect interactions.
        final SignedJwt idToken = extractIdToken(accessTokenResponse);

        return new OAuth2Session(time, providerName, clientEndpoint, null, actualScopes,
                accessTokenResponse, idToken, expiresAt);
    }

    OAuth2Session stateAuthorizing(final String providerName, final String clientEndpoint,
            final String authorizationRequestNonce, final List<String> requestedScopes) {
        return new OAuth2Session(time, providerName, clientEndpoint, authorizationRequestNonce,
                requestedScopes, accessTokenResponse, idToken, expiresAt);
    }

    OAuth2Session stateRefreshed(final JsonValue refreshTokenResponse) throws OAuth2ErrorException {
        return stateAuthorized(refreshTokenResponse);
    }

    private long now() {
        return SECONDS.convert(time.now(), MILLISECONDS);
    }
}
