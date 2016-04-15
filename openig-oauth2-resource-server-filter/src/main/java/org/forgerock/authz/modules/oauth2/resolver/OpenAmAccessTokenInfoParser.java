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
package org.forgerock.authz.modules.oauth2.resolver;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Set;

import org.forgerock.authz.modules.oauth2.AccessTokenInfo;
import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.util.Function;
import org.forgerock.util.time.TimeService;

/**
 * Parser for {@link AccessTokenInfo}.
 * <p>
 * Models an {@link AccessTokenInfo} as returned by the OpenAM {@literal tokeninfo} endpoint.
 * <pre>
 *     {@code
 *     curl https://openam.example.com:8443/openam/oauth2/tokeninfo?access_token=70e5776c-b0fa-4c70-9962-defb0e9c3cd6
 *     }
 * </pre>
 *
 * Example of OpenAM returned Json value (for the previous request):
 * <pre>
 *     {@code
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
 *     }
 * </pre>
 */
class OpenAmAccessTokenInfoParser implements Function<JsonValue, AccessTokenInfo, AccessTokenException> {

    private final TimeService time;

    /**
     * Creates a new parser with the given {@link TimeService}.
     *
     * @param time
     *         time service used to compute the expiration date
     */
    public OpenAmAccessTokenInfoParser(final TimeService time) {
        this.time = time;
    }

    /**
     * Creates a new {@link AccessTokenInfo} from a raw JSON response returned by the {@literal /oauth2/tokeninfo}
     * endpoint.
     *
     * @param raw
     *         JSON response
     * @return a new {@link AccessTokenInfo}
     * @throws AccessTokenException
     *         if the JSON response is not formatted correctly.
     */
    @Override
    public AccessTokenInfo apply(final JsonValue raw) throws AccessTokenException {
        try {
            final long expiresIn = raw.get("expires_in").required().asLong();
            final Set<String> scopes = raw.get("scope").required().asSet(String.class);
            final String token = raw.get("access_token").required().asString();

            return new AccessTokenInfo(raw, token, scopes, getExpirationTime(expiresIn));
        } catch (JsonValueException e) {
            throw new AccessTokenException("Cannot build AccessToken from the given JSON: invalid format", e);
        }
    }

    private long getExpirationTime(final long delayInSeconds) {
        return time.now() + MILLISECONDS.convert(delayInSeconds, SECONDS);
    }
}
