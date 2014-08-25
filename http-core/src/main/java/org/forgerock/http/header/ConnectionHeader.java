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

import java.util.ArrayList;
import java.util.List;

import org.forgerock.http.Header;
import org.forgerock.http.Message;

/**
 * Processes the <strong>{@code Connection}</strong> message header. For more
 * information, see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>
 * §14.10.
 */
public class ConnectionHeader implements Header {
    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static ConnectionHeader valueOf(final Message message) {
        return new ConnectionHeader(parseMultiValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static ConnectionHeader valueOf(final String string) {
        return new ConnectionHeader(parseMultiValuedHeader(string));
    }

    /** The name of this header. */
    public static final String NAME = "Connection";

    /** A list of connection tokens. */
    private final List<String> tokens;

    /**
     * Constructs a new empty header.
     */
    public ConnectionHeader() {
        this(new ArrayList<String>(1));
    }

    /**
     * Constructs a new header with the provided connection tokens.
     *
     * @param tokens
     *            The connection tokens.
     */
    public ConnectionHeader(final List<String> tokens) {
        this.tokens = tokens;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Returns the list of connection tokens.
     *
     * @return The list of connection tokens.
     */
    public List<String> getTokens() {
        return tokens;
    }

    @Override
    public String toString() {
        // will return null if empty
        return HeaderUtil.join(tokens, ',');
    }
}
