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
 * Builds an error {@link Response} when the request is invalid (missing param, malformed, ...).
 * <p>
 * Example:
 * <pre>
 *     HTTP/1.1 400 Bad Request
 *     WWW-Authenticate: Bearer realm="example",
 *                              error="invalid_request",
 *                              error_description="...."
 * </pre>
 */
public class InvalidRequestChallengeHandler extends AuthenticateChallengeHandler {

    private static final String INVALID_REQUEST_DESCRIPTION = "The request is missing a required parameter, "
            + "includes an unsupported parameter or parameter value, repeats the same parameter, "
            + "uses more than one method for including an access token, or is otherwise malformed.";

    /**
     * Builds a new InvalidRequestChallengeHandler with a default error description and no error page URI.
     *
     * @param realm
     *         mandatory realm value.
     */
    public InvalidRequestChallengeHandler(final String realm) {
        this(realm, null);
    }

    /**
     * Builds a new InvalidRequestChallengeHandler with a default error description.
     *
     * @param realm
     *         mandatory realm value.
     * @param invalidRequestUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InvalidRequestChallengeHandler(final String realm,
                                          final String invalidRequestUri) {
        this(realm, INVALID_REQUEST_DESCRIPTION, invalidRequestUri);
    }

    /**
     * Builds a new InvalidRequestChallengeHandler.
     *
     * @param realm
     *         mandatory realm value.
     * @param description
     *         error description (will be omitted if {@literal null})
     * @param invalidRequestUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InvalidRequestChallengeHandler(final String realm,
                                          final String description,
                                          final String invalidRequestUri) {
        super(realm, "invalid_request", description, invalidRequestUri);
    }

    @Override
    protected Response createResponse() {
        Response response = new Response();
        response.setStatus(400);
        response.setReason("Bad Request");
        return response;
    }

}
