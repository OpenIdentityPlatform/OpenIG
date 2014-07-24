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
 * Builds an error {@link Response} when the request does not contains any OAuth 2.0 Bearer token.
 * <p>
 * Example:
 * <pre>
 *     HTTP/1.1 401 Unauthorized
 *     WWW-Authenticate: Bearer realm="example"
 * </pre>
 */
public class NoAuthenticationChallengeHandler extends AuthenticateChallengeHandler {

    /**
     * Builds a new NoAuthenticationChallengeHandler.
     *
     * @param realm
     *         mandatory realm value.
     */
    public NoAuthenticationChallengeHandler(final String realm) {
        super(realm, null, null, null);
    }

    @Override
    public Response createResponse() {
        Response response = new Response();
        response.status = 401;
        response.reason = "Unauthorized";
        return response;
    }
}
