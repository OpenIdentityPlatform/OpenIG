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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Header;
import org.forgerock.http.Message;

/**
 * Processes the <strong>{@code Content-Type}</strong> message header. For more
 * information, see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>
 * §14.17.
 */
public class ContentTypeHeader implements Header {

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static ContentTypeHeader valueOf(final Message message) {
        return valueOf(parseSingleValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static ContentTypeHeader valueOf(final String string) {
        List<String> parts = HeaderUtil.split(string, ';');
        if (parts.size() > 0) {
            String type = parts.get(0);
            final Map<String, String> parameters = HeaderUtil.parseParameters(parts);
            String charset = parameters.get("charset");
            String boundary = parameters.get("boundary");
            return new ContentTypeHeader(type, charset, boundary);
        } else {
            return new ContentTypeHeader(null, null, null);
        }
    }

    /** The name of this header. */
    public static final String NAME = "Content-Type";

    /** The type/sub-type of the message. */
    private final String type;

    /** The character set used in encoding the message. */
    private final String charset;

    /** The boundary value provided in multipart messages. */
    private final String boundary;

    /**
     * Constructs a new empty header whose type, charset, and boundary are all
     * {@code null}.
     */
    public ContentTypeHeader() {
        this(null, null, null);
    }

    /**
     * Constructs a new header with the provided parameters.
     *
     * @param type
     *            The type/sub-type of the message.
     * @param charset
     *            The character set used in encoding the message.
     * @param boundary
     *            The boundary value provided in multipart messages.
     */
    public ContentTypeHeader(String type, String charset, String boundary) {
        this.type = type;
        this.charset = charset;
        this.boundary = boundary;
    }

    /**
     * Returns the media type of the underlying data or {@code null} if none
     * specified.
     *
     * @return The media type of the underlying data or {@code null} if none
     *         specified.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the character set encoding used to encode the message, or
     * {@code null} if no character set was specified.
     *
     * @return The character set encoding used to encode the message or
     *         {@code null} if empty.
     * @throws java.nio.charset.IllegalCharsetNameException
     *             if the given charset name is illegal.
     * @throws java.nio.charset.UnsupportedCharsetException
     *             if no support for the named charset is available.
     */
    public Charset getCharset() {
        return charset != null ? Charset.forName(charset) : null;
    }

    /**
     * Returns the encapsulation boundary or {@code null} if none specified.
     *
     * @return The encapsulation boundary or {@code null} if none specified.
     */
    public String getBoundary() {
        return boundary;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        if (type == null || type.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        if (charset != null) {
            sb.append("; charset=").append(charset);
        }
        if (boundary != null) {
            sb.append("; boundary=").append(boundary);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
