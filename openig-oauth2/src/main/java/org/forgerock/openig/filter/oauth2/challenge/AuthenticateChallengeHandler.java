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
import static org.forgerock.util.Reject.*;

import java.io.IOException;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;

/**
 * This handler build an authentication challenge to be returned in the {@link Response} {@literal Authorization} HTTP
 * header.
 * <p>
 * It has to be sub-classed in order to create a {@link Response} with the appropriate status code and reason phrase.
 */
public abstract class AuthenticateChallengeHandler implements Handler {

    /**
     * Authorization HTTP Header name.
     */
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private final String realm;
    private final String error;
    private final String description;
    private final String uri;

    /**
     * Creates a new AuthenticateChallengeHandler. The realm must not be {@literal null}.
     *
     * @param realm
     *         mandatory realm value.
     * @param error
     *         error code (will be omitted if {@literal null})
     * @param description
     *         error description (will be omitted if {@literal null})
     * @param uri
     *         error uri page (will be omitted if {@literal null})
     */
    protected AuthenticateChallengeHandler(final String realm,
                                           final String error,
                                           final String description,
                                           final String uri) {
        this.realm = checkNotNull(realm, "OAuth2 Challenge needs a realm");
        this.error = error;
        this.description = description;
        this.uri = uri;
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        exchange.response = createResponse();
        exchange.response.getHeaders().putSingle(WWW_AUTHENTICATE,
                                            format("Bearer %s", buildChallenge()));
    }

    /**
     * Creates a {@link Response} with the appropriate status code and reason. This method is called each time the
     * {@link #handle(Exchange)} method is invoked.
     *
     * @return a new initialized {@link Response} instance
     */
    protected abstract Response createResponse();

    private String buildChallenge() {
        StringBuilder sb = new StringBuilder();

        appendRealm(sb);
        appendError(sb);
        appendErrorDescription(sb);
        appendErrorUri(sb);
        appendExtraAttributes(sb);

        return sb.toString();
    }

    private void appendRealm(final StringBuilder sb) {
        sb.append(format("realm=\"%s\"", realm));
    }

    private void appendError(final StringBuilder sb) {
        if (error != null) {
            sb.append(format(", error=\"%s\"", error));
        }
    }

    private void appendErrorDescription(final StringBuilder sb) {
        if (description != null) {
            sb.append(format(", error_description=\"%s\"", description));
        }
    }

    /**
     * Permits sub-classes to append extra attributes to the challenge.
     * @param sb Challenge value
     */
    protected void appendExtraAttributes(final StringBuilder sb) {
        // For sub-classes to add extra attributes
    }

    private void appendErrorUri(final StringBuilder sb) {
        if (uri != null) {
            sb.append(format(", error_uri=\"%s\"", uri));
        }
    }

}
