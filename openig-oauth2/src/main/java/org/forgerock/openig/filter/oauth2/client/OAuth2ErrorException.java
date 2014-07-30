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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.newAuthorizationServerError;

/**
 * An exception that is thrown when OAuth 2.0 request fails.
 */
public final class OAuth2ErrorException extends Exception {
    private static final long serialVersionUID = 1L;
    private final OAuth2Error error;

    /**
     * Creates a new exception with the provided OAuth 2.0 error.
     *
     * @param error
     *            The OAuth 2.0 error.
     */
    public OAuth2ErrorException(final OAuth2Error error) {
        super(error.toString());
        this.error = error;
    }

    /**
     * Creates a new exception with the provided OAuth 2.0 error.
     *
     * @param error
     *            The OAuth 2.0 error.
     * @param message
     *            The message.
     */
    public OAuth2ErrorException(final OAuth2Error error, final String message) {
        super(message);
        this.error = error;
    }

    /**
     * Creates a new exception with the provided OAuth 2.0 error.
     *
     * @param error
     *            The OAuth 2.0 error.
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    public OAuth2ErrorException(final OAuth2Error error, final String message, final Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    /**
     * Creates a new exception with the provided OAuth 2.0 error.
     *
     * @param error
     *            The OAuth 2.0 error.
     * @param cause
     *            The cause.
     */
    public OAuth2ErrorException(final OAuth2Error error, final Throwable cause) {
        super(error.toString(), cause);
        this.error = error;
    }

    /**
     * Creates a new exception with the provided OAuth 2.0 error code and
     * optional description.
     *
     * @param error
     *            The error code specifying the cause of the failure.
     * @param errorDescription
     *            The human-readable ASCII text providing additional
     *            information, or {@code null}.
     * @throws NullPointerException
     *             If {@code error} was {@code null}.
     */
    public OAuth2ErrorException(final String error, final String errorDescription) {
        this(newAuthorizationServerError(error, errorDescription));
    }

    /**
     * Creates a new exception with the provided OAuth 2.0 error code, optional
     * description, and cause.
     *
     * @param error
     *            The error code specifying the cause of the failure.
     * @param errorDescription
     *            The human-readable ASCII text providing additional
     *            information, or {@code null}.
     * @param cause
     *            The cause.
     * @throws NullPointerException
     *             If {@code error} was {@code null}.
     */
    public OAuth2ErrorException(final String error, final String errorDescription,
            final Throwable cause) {
        this(newAuthorizationServerError(error, errorDescription), cause);
    }

    /**
     * Returns the OAuth 2.0 error represented by this exception.
     *
     * @return The OAuth 2.0 error represented by this exception.
     */
    public OAuth2Error getOAuth2Error() {
        return error;
    }

}
