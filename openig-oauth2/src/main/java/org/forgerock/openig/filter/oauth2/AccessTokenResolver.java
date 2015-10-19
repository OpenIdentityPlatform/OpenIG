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

package org.forgerock.openig.filter.oauth2;

import org.forgerock.services.context.Context;

/**
 * Resolves a given token against a dedicated OAuth2 Identity Provider (OpenAM, Google, Facebook, ...).
 */
public interface AccessTokenResolver {

    /**
     * Resolves a given access token against a authorization server.
     *
     * @param context Context chain used to keep a relationship between requests (tracking)
     * @param token token identifier to be resolved
     * @return a valid {@link AccessToken} (well formed, known by the server)
     * @throws OAuth2TokenException
     *         if the token is unknown by the server
     */
    AccessToken resolve(Context context, String token) throws OAuth2TokenException;
}
