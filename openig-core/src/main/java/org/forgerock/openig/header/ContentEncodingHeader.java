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

package org.forgerock.openig.header;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.openig.decoder.Decoder;
import org.forgerock.openig.http.Message;

/**
 * Processes the <strong>{@code Content-Encoding}</strong> message header. For more information, see
 * <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.11.
 */
public class ContentEncodingHeader implements Header {

    /** The name of the header that this object represents. */
    public static final String NAME = "Content-Encoding";

    /** The content-coding(s), in the order they are applied to the entity. */
    private final List<String> codings = new ArrayList<String>();

    /**
     * Constructs a new empty header.
     */
    public ContentEncodingHeader() {
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message the message to initialize the header from.
     */
    public ContentEncodingHeader(Message message) {
        fromMessage(message);
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string the value to initialize the header from.
     */
    public ContentEncodingHeader(String string) {
        fromString(string);
    }

    /**
     * Returns an input stream that decodes the specified input stream, given the
     * content-codings that are specified in the {@code codings} list.
     *
     * @param in the input stream to decode.
     * @return an input stream that provides the decoded content.
     * @throws IOException if an I/O exception occurs.
     * @throws UnsupportedEncodingException if an unsupported content-encoding is specified.
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

    private void clear() {
        codings.clear();
    }

    /**
     * Returns the content-coding(s).
     *
     * @return The list of the content-coding(s).
     */
    public List<String> getCodings() {
        return codings;
    }

    @Override
    public String getKey() {
        return NAME;
    }

    @Override
    public void fromMessage(Message message) {
        if (message != null && message.headers != null) {
            fromString(HeaderUtil.join(message.headers.get(NAME), ','));
        }
    }

    @Override
    public void fromString(String string) {
        clear();
        if (string != null) {
            codings.addAll(HeaderUtil.split(string, ','));
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
        // will return null if empty
        return HeaderUtil.join(codings, ',');
    }

    @Override
    public boolean equals(Object o) {
        return o == this
                || (o instanceof ContentEncodingHeader
                        && codings.equals(((ContentEncodingHeader) o).codings));
    }

    @Override
    public int hashCode() {
        return codings.hashCode();
    }
}
