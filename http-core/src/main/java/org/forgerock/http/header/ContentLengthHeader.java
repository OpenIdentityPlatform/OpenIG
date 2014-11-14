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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http.header;

import static org.forgerock.http.header.HeaderUtil.parseSingleValuedHeader;

import org.forgerock.http.Header;
import org.forgerock.http.Message;

/**
 * Processes the <strong>{@code Content-Length}</strong> message header. For
 * more information, see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC
 * 2616</a> §14.13.
 */
public class ContentLengthHeader implements Header {
    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static ContentLengthHeader valueOf(final Message message) {
        return valueOf(parseSingleValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static ContentLengthHeader valueOf(final String string) {
        long length = -1;
        if (string != null) {
            try {
                length = Long.parseLong(string);
                length = length >= 0 ? length : -1;
            } catch (NumberFormatException nfe) {
                // will remain default of -1 from clear() call above
            }
        }
        return new ContentLengthHeader(length);
    }

    /** The name of this header. */
    public static final String NAME = "Content-Length";

    /** The content length, or {@code -1} if not specified. */
    private long length;

    /**
     * Constructs a new empty header whose length is set to -1.
     */
    public ContentLengthHeader() {
        this(-1);
    }

    /**
     * Constructs a new header with the provided content length.
     *
     * @param length
     *            The content length, or {@code -1} if no content length has
     *            been set.
     */
    public ContentLengthHeader(long length) {
        this.length = length;
    }

    /**
     * Returns the content length, or {@code -1} if no content length has been
     * set.
     *
     * @return The content length, or {@code -1} if no content length has been
     *         set.
     */
    public long getLength() {
        return length;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return length >= 0 ? Long.toString(length) : null;
    }
}
