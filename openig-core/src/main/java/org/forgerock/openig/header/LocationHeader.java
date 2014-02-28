/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012-2014 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openig.header;

import org.forgerock.openig.http.Message;

/**
 * Processes the <strong>{@code Location}</strong> message header. For more information see
 * <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.30.
 *
 * @author Mark de Reeper
 */
public final class LocationHeader implements Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "Location";

    /** The location URI value from the header, or empty string if not specified. */
    public String locationURI = "";

    /**
     * Constructs a new empty <strong>{@code LocationHeader}</strong>.
     */
    public LocationHeader() {
    }

    /**
     * Constructs a new <strong>{@code LocationHeader}</strong>, initialised from the specified message.
     *
     * @param message the message to initialise the header from.
     */
    public LocationHeader(Message message) {
        fromMessage(message);
    }

    /**
     * Constructs a new <strong>{@code LocationHeader}</strong>, initialised from the specified String value.
     *
     * @param string the value to initialise the header from.
     */
    public LocationHeader(String string) {
        fromString(string);
    }

    private void clear() {
        locationURI = "";
    }

    @Override
    public String getKey() {
        return NAME;
    }

    @Override
    public void fromMessage(Message message) {
        if (message != null && message.headers != null) {
            fromString(message.headers.getFirst(NAME)); // expect only one header value
        }
    }

    @Override
    public void fromString(String string) {
        clear();
        if (string != null) {
            locationURI = string;
        }
    }

    @Override
    public void toMessage(Message message) {
        String value = toString();
        if (value != null) {
            message.headers.putSingle(NAME, value);
        }
    }

    @Override
    public String toString() {
        return ("".equals(locationURI) ? null : locationURI);
    }

    @Override
    public boolean equals(Object o) {
        return (o == this || (o != null && o instanceof LocationHeader &&
         this.locationURI.equals(((LocationHeader)o).locationURI)));
    }

    @Override
    public int hashCode() {
        return locationURI.hashCode();
    }
}
