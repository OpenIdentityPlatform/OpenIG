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

import static org.forgerock.http.header.HeaderUtil.parseMultiValuedHeader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.http.Header;
import org.forgerock.http.Message;
import org.forgerock.http.decoder.Decoder;

/**
 * Processes the <strong>{@code Content-Encoding}</strong> message header. For
 * more information, see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC
 * 2616</a> §14.11.
 */
public class ContentEncodingHeader implements Header {
    /** The name of this header. */
    public static final String NAME = "Content-Encoding";

    /** The content coding, in the order they are applied to the entity. */
    private final List<String> codings;

    /**
     * Constructs a new empty header.
     */
    public ContentEncodingHeader() {
        this(new ArrayList<String>(1));
    }

    /**
     * Constructs a new header with the provided content encodings.
     *
     * @param codings
     *            The content encodings.
     */
    public ContentEncodingHeader(final List<String> codings) {
        this.codings = codings;
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static ContentEncodingHeader valueOf(final Message message) {
        return new ContentEncodingHeader(parseMultiValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static ContentEncodingHeader valueOf(final String string) {
        return new ContentEncodingHeader(parseMultiValuedHeader(string));
    }

    /**
     * Returns an input stream that decodes the specified input stream, given
     * the content-codings that are specified in the {@code codings} list.
     *
     * @param in
     *            the input stream to decode.
     * @return an input stream that provides the decoded content.
     * @throws IOException
     *             if an I/O exception occurs.
     * @throws UnsupportedEncodingException
     *             if an unsupported content-encoding is specified.
     */
    public InputStream decode(InputStream in) throws IOException, UnsupportedEncodingException {
        // decode in the reverse order that encoding was applied
        for (ListIterator<String> i = codings.listIterator(codings.size()); i.hasPrevious();) {
            String name = i.previous();
            Decoder decoder = Decoder.SERVICES.get(name);
            if (decoder == null) {
                throw new UnsupportedEncodingException(name);
            }
            in = decoder.decode(in);
        }
        return in;
    }

    /**
     * Returns the list of content codings.
     *
     * @return The list of content codings.
     */
    public List<String> getCodings() {
        return codings;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        // will return null if empty
        return HeaderUtil.join(codings, ',');
    }
}
