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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.authz.modules.oauth2;

import static org.forgerock.util.Reject.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.JsonValue;

/** Represents an <a href="http://tools.ietf.org/html/rfc6749#section-1.4">OAuth2 Access Token</a>. */
public class AccessTokenInfo {

    /** Marker for never ending tokens. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private final JsonValue rawInfo;
    private final String token;
    private final Set<String> scopes;
    private final long expiresAt;

    /**
     * Build an {@link AccessTokenInfo} with the provided information.
     *
     * @param rawInfo
     *         raw response message
     * @param token
     *         token identifier
     * @param scopes
     *         scopes of the token
     * @param expiresAt
     *         Token expiration time expressed as a timestamp, in milliseconds since epoch
     */
    public AccessTokenInfo(final JsonValue rawInfo,
                              final String token,
                              final Set<String> scopes,
                              final long expiresAt) {
        this.rawInfo = checkNotNull(rawInfo).clone();
        this.token = checkNotNull(token);
        this.scopes = Collections.unmodifiableSet(checkNotNull(scopes));
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the raw JSON as a map.
     *
     * @return the raw JSON as a map.
     */
    public Map<String, Object> getInfo() {
        return rawInfo.asMap();
    }

    /**
     * Returns the raw JSON as a {@link JsonValue}.
     *
     * @return the raw JSON as a {@link JsonValue}.
     */
    public JsonValue asJsonValue() {
        return rawInfo;
    }

    /**
     * Returns the access token identifier issued from the authorization server.
     *
     * @return the access token identifier issued from the authorization server.
     */
    public String getToken() {
        return token;
    }

    /**
     * Returns the scopes associated to this token. Will return an empty Set if no scopes are associated with this
     * token.
     *
     * @return the scopes associated to this token.
     */
    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * Returns the time (expressed as a timestamp in milliseconds since epoch) when this token will be expired. If the
     * {@link #NEVER_EXPIRES} constant is returned, this token is always considered as available.
     *
     * @return the time (expressed as a timestamp, in milliseconds since epoch) when this token will be expired.
     */
    public long getExpiresAt() {
        return expiresAt;
    }
}
