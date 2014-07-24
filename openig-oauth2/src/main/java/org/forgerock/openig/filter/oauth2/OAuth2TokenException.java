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

/**
 * Represents an exception whilst retrieving an OAuth2 access token.
 */
public class OAuth2TokenException extends Exception {

    /**
     * Serial Version UID.
     */
    public static final long serialVersionUID = -1L;

    private final String error;
    private final String description;

    /**
     * Builds an {@link OAuth2TokenException} with a given error message.
     *
     * @param error
     *         error identifier
     */
    public OAuth2TokenException(final String error) {
        this(error, null);
    }

    /**
     * Builds an {@link OAuth2TokenException} with a given error message and description.
     *
     * @param error
     *         error identifier
     * @param description
     *         error description
     */
    public OAuth2TokenException(final String error, final String description) {
        this(error, description, null);
    }

    /**
     * Builds an {@link OAuth2TokenException} with a given error message, description and cause.
     *
     * @param error
     *         error identifier
     * @param description
     *         error description
     * @param cause
     *         error cause
     */
    public OAuth2TokenException(final String error, final String description, final Exception cause) {
        super(error, cause);
        this.error = error;
        this.description = description;
    }

    /**
     * Returns the error code.
     *
     * @return the error code.
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the error description (may be {@literal null}).
     *
     * @return the error description (may be {@literal null}).
     */
    public String getDescription() {
        return description;
    }
}
