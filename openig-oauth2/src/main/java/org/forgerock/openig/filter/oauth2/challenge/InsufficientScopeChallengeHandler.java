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

import static java.lang.String.*;

import java.util.Collections;
import java.util.Set;

import org.forgerock.openig.http.Response;

/**
 * Builds an error {@link Response} when the request is missing some required scope.
 * <p>
 * Example:
 * <pre>
 *     HTTP/1.1 403 Forbidden
 *     WWW-Authenticate: Bearer realm="example",
 *                              error="insufficient_scope",
 *                              error_description="....",
 *                              scope="openid profile email"
 * </pre>
 */
public class InsufficientScopeChallengeHandler extends AuthenticateChallengeHandler {

    private static final String INSUFFICIENT_SCOPE_DESCRIPTION = "The request requires higher privileges than "
            + "provided by the access token.";

    private final Set<String> scopes;

    /**
     * Builds a new InsufficientScopeChallengeHandler with a default description, no error URI page and no scopes.
     *
     * @param realm
     *         mandatory realm value.
     */
    public InsufficientScopeChallengeHandler(final String realm) {
        this(realm, Collections.<String>emptySet());
    }

    /**
     * Builds a new InsufficientScopeChallengeHandler with a default description and no error URI page.
     *
     * @param realm
     *         mandatory realm value.
     * @param scopes
     *         List of required scopes (will be omitted if empty)
     */
    public InsufficientScopeChallengeHandler(final String realm, final Set<String> scopes) {
        this(realm, scopes, null);
    }

    /**
     * Builds a new InsufficientScopeChallengeHandler with a default description.
     *
     * @param realm
     *         mandatory realm value.
     * @param scopes
     *         List of required scopes (will be omitted if empty)
     * @param insufficientScopeUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InsufficientScopeChallengeHandler(final String realm,
                                             final Set<String> scopes,
                                             final String insufficientScopeUri) {
        this(realm, INSUFFICIENT_SCOPE_DESCRIPTION, scopes, insufficientScopeUri);
    }

    /**
     * Builds a new InsufficientScopeChallengeHandler.
     *
     * @param realm
     *         mandatory realm value.
     * @param description
     *         error description (will be omitted if {@literal null})
     * @param scopes
     *         List of required scopes (will be omitted if empty)
     * @param insufficientScopeUri
     *         error uri page (will be omitted if {@literal null})
     */
    public InsufficientScopeChallengeHandler(final String realm,
                                             final String description,
                                             final Set<String> scopes,
                                             final String insufficientScopeUri) {
        super(realm, "insufficient_scope", description, insufficientScopeUri);
        this.scopes = scopes;
    }

    @Override
    protected Response createResponse() {
        Response response = new Response();
        response.status = 403;
        response.reason = "Forbidden";
        return response;
    }

    @Override
    protected void appendExtraAttributes(final StringBuilder sb) {
        StringBuilder scopeAttr = buildSpaceSeparatedScopeList();
        if (scopeAttr.length() != 0) {
            sb.append(format(", scope=\"%s\"", scopeAttr.toString()));
        }
    }

    private StringBuilder buildSpaceSeparatedScopeList() {
        StringBuilder scopeAttr = new StringBuilder();
        for (String scope : scopes) {
            if (scopeAttr.length() != 0) {
                scopeAttr.append(" ");
            }
            scopeAttr.append(scope);
        }
        return scopeAttr;
    }


}
