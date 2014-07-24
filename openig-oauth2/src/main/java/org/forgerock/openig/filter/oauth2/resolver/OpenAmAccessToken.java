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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.resolver;

import static java.util.concurrent.TimeUnit.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.util.time.TimeService;

/**
 * Models an {@link AccessToken} as returned by the OpenAM {@literal tokeninfo} endpoint.
 * <pre>
 *     curl https://openam.example.com:8443/openam/oauth2/tokeninfo?access_token=70e5776c-b0fa-4c70-9962-defb0e9c3cd6
 * </pre>
 *
 * Example of OpenAM returned Json value (for the previous request):
 * <pre>
 *     {
 *         "scope": [
 *             "email",
 *             "profile"
 *         ],
 *         "grant_type": "password",
 *         "realm": "/",
 *         "token_type": "Bearer",
 *         "expires_in": 471,
 *         "access_token": "70e5776c-b0fa-4c70-9962-defb0e9c3cd6",
 *         "email": "",
 *         "profile": ""
 *     }
 * </pre>
 */
public class OpenAmAccessToken implements AccessToken {

    private final JsonValue rawInfo;
    private final String token;
    private final Set<String> scopes;
    private final long expiresAt;

    /**
     * Builds a {@link AccessToken} with the result of a call to the {@literal tokeninfo} endpoint.
     *
     * @param rawInfo
     *         raw response message.
     * @param token
     *         token identifier
     * @param scopes
     *         scopes of the token
     * @param expiresAt
     *         When this token will expires
     */
    public OpenAmAccessToken(final JsonValue rawInfo,
                             final String token,
                             final Set<String> scopes,
                             final long expiresAt) {
        this.rawInfo = rawInfo;
        this.token = token;
        this.scopes = Collections.unmodifiableSet(scopes);
        this.expiresAt = expiresAt;
    }

    @Override
    public JsonValue getRawInfo() {
        return rawInfo;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public Set<String> getScopes() {
        return scopes;
    }

    @Override
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Build helper for {@link OpenAmAccessToken}.
     */
    public static class Builder {

        private final TimeService time;

        /**
         * Creates a new Builder with the given {@link TimeService}.
         *
         * @param time time service used to compute the expiration date
         */
        public Builder(final TimeService time) {
            this.time = time;
        }

        /**
         * Builds a {@link OpenAmAccessToken} from a raw JSON response returned by the {@literal /oauth2/tokeninfo}
         * endpoint.
         *
         * @param raw
         *         JSON response
         * @return a new {@link OpenAmAccessToken}
         * @throws OAuth2TokenException
         *         if the JSON response is not formatted correctly.
         */
        public OpenAmAccessToken build(final JsonValue raw) throws OAuth2TokenException {
            try {
                long expiresIn = raw.get("expires_in").required().asLong();
                Set<String> scopes = new HashSet<String>(raw.get("scope").required().asList(String.class));
                String token = raw.get("access_token").required().asString();
                return new OpenAmAccessToken(raw,
                                             token,
                                             scopes,
                                             getExpirationTime(expiresIn));
            } catch (JsonValueException e) {
                throw new OAuth2TokenException(null, "Cannot build AccessToken from the given JSON: invalid format", e);
            }
        }

        private long getExpirationTime(final long delayInSeconds) {
            return time.now() + MILLISECONDS.convert(delayInSeconds, SECONDS);
        }

    }
}
