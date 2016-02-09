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

import java.util.Set;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.services.context.Context;

/** A {@link ResourceAccess} encapsulates the logic of required scope selection. */
public interface ResourceAccess {

    /**
     * Returns the scopes required to access the resource.
     *
     * @param context
     *         The current context which might be used to retrieve required scopes.
     * @param request
     *         The current OAuth2 request which might be used to retrieve required scopes.
     * @return Scopes required to access the resource. Should never return {@code null}.
     * @throws ResponseException
     *         If an error occurred while resolving scope set
     */
    Set<String> getRequiredScopes(final Context context, final Request request) throws ResponseException;
}
