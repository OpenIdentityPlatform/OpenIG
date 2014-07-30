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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.util.time.TimeService;

/**
 * Represents the current state of a user's OAuth 2 authorization.
 */
final class OAuth2Session {
    static OAuth2Session stateNew(final TimeService time) {
        return new OAuth2Session(time, null, null, null, Collections.<String> emptyList(),
                Collections.<String> emptyList(), new JsonValue(Collections.emptyMap()), null, null);
    }

    private final JsonValue accessTokenResponse;
    private final List<String> actualScopes;
    private final String authorizationRequestNonce;
    private final String clientEndpoint;
    private final Long expiresAt;
    private final SignedJwt idToken;
    private final String providerName;
    private final List<String> requestedScopes;
    private final TimeService time;

    private OAuth2Session(final TimeService time, final String providerName,
            final String clientEndpoint, final String authorizationRequestNonce,
            final List<String> requestedScopes, final List<String> actualScopes,
            final JsonValue accessTokenResponse, final SignedJwt idToken, final Long expiresAt) {
        this.time = time;
        this.providerName = providerName;
        this.clientEndpoint = clientEndpoint;
        this.authorizationRequestNonce = authorizationRequestNonce;
        this.requestedScopes = requestedScopes;
        this.accessTokenResponse = accessTokenResponse;
        this.actualScopes = actualScopes;
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
        return actualScopes;
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

    OAuth2Session stateAuthorized(final JsonValue newAccessTokenResponse, final SignedJwt idToken) {
        // Merge old token response with new.
        final Map<String, Object> mergedAccessTokenResponse =
                new LinkedHashMap<String, Object>(accessTokenResponse.asMap());
        mergedAccessTokenResponse.putAll(newAccessTokenResponse.asMap());
        final Map<String, Object> accessTokenResponse =
                Collections.unmodifiableMap(mergedAccessTokenResponse);

        // Compute effective scopes.
        final JsonValue returnedScopes = newAccessTokenResponse.get("scope");
        final List<String> actualScopes;
        if (returnedScopes.isString()) {
            actualScopes = Arrays.asList(returnedScopes.asString().trim().split("\\s+"));
        } else {
            actualScopes = requestedScopes;
        }

        // Compute expiration time from expires_in field.
        final Long expiresIn = newAccessTokenResponse.get("expires_in").asLong();
        final Long expiresAt = expiresIn != null ? expiresIn + now() : null;

        return new OAuth2Session(time, providerName, clientEndpoint, null, requestedScopes,
                actualScopes, new JsonValue(accessTokenResponse), idToken, expiresAt);
    }

    OAuth2Session stateAuthorizing(final String providerName, final String clientEndpoint,
            final String authorizationRequestNonce, final List<String> requestedScopes) {
        return new OAuth2Session(time, providerName, clientEndpoint, authorizationRequestNonce,
                requestedScopes, actualScopes, accessTokenResponse, idToken, expiresAt);
    }

    OAuth2Session stateRefreshed(final JsonValue refreshTokenResponse, final SignedJwt idToken) {
        return stateAuthorized(refreshTokenResponse, idToken);
    }

    private long now() {
        return SECONDS.convert(time.now(), MILLISECONDS);
    }
}
