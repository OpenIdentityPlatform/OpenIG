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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.http.header;

import static org.forgerock.http.header.HeaderUtil.parseSingleValuedHeader;

import org.forgerock.http.Header;
import org.forgerock.http.Message;

/**
 * Processes the <strong>{@code Location}</strong> message header. For more
 * information see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>
 * §14.30.
 */
public final class LocationHeader implements Header {

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static LocationHeader valueOf(final Message message) {
        return valueOf(parseSingleValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static LocationHeader valueOf(final String string) {
        return new LocationHeader(string);
    }

    /** The name of this header. */
    public static final String NAME = "Location";

    /**
     * The location URI value from the header, or {@code null}.
     */
    private final String locationUri;

    /**
     * Constructs a new empty header whose location is {@code null}.
     */
    public LocationHeader() {
        this(null);
    }

    /**
     * Constructs a new header with the provided location URI.
     *
     * @param locationUri
     *            The location URI, or {@code null} if no location has been set.
     */
    public LocationHeader(String locationUri) {
        this.locationUri = locationUri != null && locationUri.isEmpty() ? null : locationUri;
    }

    /**
     * Returns the location URI or {@code null} if empty.
     *
     * @return The location URI or {@code null} if empty.
     */
    public String getLocationUri() {
        return locationUri;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return locationUri;
    }
}
