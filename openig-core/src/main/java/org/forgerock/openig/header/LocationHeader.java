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

package org.forgerock.openig.header;

import org.forgerock.openig.http.Message;

/**
 * Processes the <strong>{@code Location}</strong> message header. For more information see
 * <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.30.
 */
public final class LocationHeader implements Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "Location";

    /** The location URI value from the header, or empty string if not specified. */
    private String locationURI = "";

    /** Constructs a new empty <strong>{@code LocationHeader}</strong>. **/
    public LocationHeader() {
        // Nothing to do.
    }

    /**
     * Constructs a new <strong>{@code LocationHeader}</strong>, initialized from the specified message.
     *
     * @param message The message to initialize the header from.
     */
    public LocationHeader(Message<?> message) {
        fromMessage(message);
    }

    /**
     * Constructs a new <strong>{@code LocationHeader}</strong>, initialized from the specified String value.
     *
     * @param string The value to initialize the header from.
     */
    public LocationHeader(String string) {
        fromString(string);
    }

    /**
     * Returns the location URI or {@code null} if empty.
     *
     * @return The location URI or {@code null} if empty.
     */
    public String getLocationURI() {
        return "".equals(locationURI) ? null : locationURI;
    }

    @Override
    public String getKey() {
        return NAME;
    }

    @Override
    public void fromMessage(Message<?> message) {
        if (message != null && message.getHeaders() != null) {
            // expect only one header value
            fromString(message.getHeaders().getFirst(NAME));
        }
    }

    @Override
    public void fromString(String string) {
        locationURI = "";
        if (string != null) {
            locationURI = string;
        }
    }

    @Override
    public void toMessage(Message<?> message) {
        final String value = toString();
        if (value != null) {
            message.getHeaders().putSingle(NAME, value);
        }
    }

    @Override
    public String toString() {
        return getLocationURI();
    }

    @Override
    public boolean equals(Object o) {
        return o == this
            || (o instanceof LocationHeader
                && locationURI.equals(((LocationHeader) o).locationURI));
    }

    @Override
    public int hashCode() {
        return locationURI.hashCode();
    }
}
