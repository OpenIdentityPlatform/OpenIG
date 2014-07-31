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
 * Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import org.forgerock.openig.header.Header;
import org.forgerock.openig.http.Message;

/**
 * Processes the OAuth 2.0 Bearer <strong>{@code WWW-Authenticate}</strong>
 * message header. For more information, see <a
 * href="http://tools.ietf.org/html/rfc6750#section-3">RFC 6750</a>.
 */
public class OAuth2BearerWWWAuthenticateHeader implements Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "WWW-Authenticate";

    /** The possibly null OAuth 2.0 error. */
    private OAuth2Error error;

    /**
     * Constructs a new empty header.
     */
    public OAuth2BearerWWWAuthenticateHeader() {
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            the message to initialize the header from.
     */
    public OAuth2BearerWWWAuthenticateHeader(final Message<?> message) {
        fromMessage(message);
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            the value to initialize the header from.
     */
    public OAuth2BearerWWWAuthenticateHeader(final String string) {
        fromString(string);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof OAuth2BearerWWWAuthenticateHeader) {
            final OAuth2BearerWWWAuthenticateHeader other = (OAuth2BearerWWWAuthenticateHeader) o;
            if (error == null) {
                return other.error == null;
            }
            return error.equals(((OAuth2BearerWWWAuthenticateHeader) o).error);
        } else {
            return false;
        }
    }

    @Override
    public void fromMessage(final Message<?> message) {
        if (message != null && message.getHeaders() != null) {
            fromString(message.getHeaders().getFirst(NAME));
        }
    }

    @Override
    public void fromString(final String string) {
        error = null;
        if (string != null) {
            try {
                error = OAuth2Error.valueOfWWWAuthenticateHeader(string);
            } catch (final IllegalArgumentException e) {
                // Ignore parsing errors - just reset the header.
            }
        }
    }

    @Override
    public String getKey() {
        return NAME;
    }

    /**
     * Returns the OAuth 2.0 error represented by this header.
     *
     * @return The OAuth 2.0 error represented by this header.
     */
    public OAuth2Error getOAuth2Error() {
        return error;
    }

    @Override
    public int hashCode() {
        return error != null ? error.hashCode() : 0;
    }

    @Override
    public void toMessage(final Message<?> message) {
        final String value = toString();
        if (value != null) {
            message.getHeaders().putSingle(NAME, value);
        }
    }

    @Override
    public String toString() {
        return error != null ? error.toWWWAuthenticateHeader() : null;
    }
}
