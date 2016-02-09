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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.authz.modules.oauth2;

import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * An {@link OAuth2Context} could be used to store and retrieve an {@link AccessToken}.
 * <p>
 * Once a {@link ResourceServerFilter} has authorized an OAuth2 request and
 * obtained the access token's state, the access token can be stored in the
 *  {@link OAuth2Context} in order to influence subsequent processing of the request.
 * <p>
 * For example, the information provided in the AccessToken may be used for additional fine-grained authorization.
 * <p>
 * The following code illustrates how an application may store/retrieve an access token:
 *
 * <pre>
 * AccessToken accessToken = ...;
 * Context parentContext = ...;
 * // Create the OAuth2 context and store the access token
 * OAuth2Context context = new OAuth2Context(parentContext, accessToken);
 * [...]
 * AccessToken myToken = context.asContext(OAuth2Context.class).getAccessToken();
 * </pre>
 */
public class OAuth2Context extends AbstractContext {

    private final AccessToken accessToken;

    /**
     * Creates a new OAuth2 context with the provided {@link AccessToken}.
     *
     * @param parent
     *         The parent context.
     * @param accessToken
     *         The access token to store.
     */
    public OAuth2Context(final Context parent, final AccessToken accessToken) {
        super(parent, "oauth2");
        this.accessToken = accessToken;
    }

    /**
     * Returns the access token associated with this OAuth2 context.
     *
     * @return the access token associated with this OAuth2 context.
     */
    public AccessToken getAccessToken() {
        return accessToken;
    }
}
