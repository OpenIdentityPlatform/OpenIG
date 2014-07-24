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

package org.forgerock.openig.filter.oauth2.challenge;

import org.forgerock.openig.http.Response;

/**
 * Builds an error {@link Response} when the token extracted from the request is invalid (expired, revoked, ...).
 * <p>
 * Example:
 * <pre>
 *     HTTP/1.1 401 Unauthorized
 *     WWW-Authenticate: Bearer realm="example",
 *                              error="invalid_token",
 *                              error_description="...."
 * </pre>
 */
public class InvalidTokenChallengeHandler extends AuthenticateChallengeHandler {

    private static final String INVALID_TOKEN_DESCRIPTION = "The access token provided is expired, revoked, "
            + "malformed, or invalid for other reasons.";

    /**
     * Builds a new InvalidTokenChallengeHandler with a default description and no error page URI.
     *
     * @param realm
     *         mandatory realm value.
     */
    public InvalidTokenChallengeHandler(final String realm) {
        this(realm, null);
    }

    /**
     * Builds a new InvalidTokenChallengeHandler with a default description.
     *
     * @param realm
     *         mandatory realm value.
     * @param invalidTokenUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InvalidTokenChallengeHandler(final String realm,
                                          final String invalidTokenUri) {
        this(realm, INVALID_TOKEN_DESCRIPTION, invalidTokenUri);
    }

    /**
     * Builds a new InvalidTokenChallengeHandler.
     *
     * @param realm
     *         mandatory realm value.
     * @param description
     *         error description (will be omitted if {@literal null})
     * @param invalidTokenUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InvalidTokenChallengeHandler(final String realm,
                                          final String description,
                                          final String invalidTokenUri) {
        super(realm, "invalid_token", description, invalidTokenUri);
    }

    @Override
    protected Response createResponse() {
        Response response = new Response();
        response.status = 401;
        response.reason = "Unauthorized";
        return response;
    }
}
