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
 * Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static java.util.Collections.singletonList;
import static org.forgerock.http.header.HeaderUtil.parseSingleValuedHeader;

import java.util.Collections;
import java.util.List;

import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Message;
import org.forgerock.openig.oauth2.OAuth2Error;

/**
 * Processes the OAuth 2.0 Bearer <strong>{@code WWW-Authenticate}</strong>
 * message header. For more information, see <a
 * href="http://tools.ietf.org/html/rfc6750#section-3">RFC 6750</a>.
 */
public class OAuth2BearerWWWAuthenticateHeader extends Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "WWW-Authenticate";

    /** The possibly null OAuth 2.0 error. */
    private final OAuth2Error error;

    /**
     * Constructs a new empty header.
     */
    public OAuth2BearerWWWAuthenticateHeader() {
        this(null);
    }

    /**
     * Constructs a new header with the provided error.
     *
     * @param error
     *            The possibly null OAuth 2.0 error.
     */
    public OAuth2BearerWWWAuthenticateHeader(OAuth2Error error) {
        this.error = error;
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static OAuth2BearerWWWAuthenticateHeader valueOf(final Message message) {
        return valueOf(parseSingleValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static OAuth2BearerWWWAuthenticateHeader valueOf(final String string) {
        if (string != null) {
            try {
                return new OAuth2BearerWWWAuthenticateHeader(OAuth2Error
                        .valueOfWWWAuthenticateHeader(string));
            } catch (final IllegalArgumentException e) {
                // Ignore parsing errors - just reset the header.
            }
        }
        return new OAuth2BearerWWWAuthenticateHeader();
    }

    @Override
    public String getName() {
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
    public List<String> getValues() {
        return error != null ? singletonList(error.toWWWAuthenticateHeader()) : Collections.<String>emptyList();
    }
}
