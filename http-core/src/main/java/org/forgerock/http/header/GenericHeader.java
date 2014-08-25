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

import org.forgerock.http.Header;

/**
 * An undecoded HTTP message header.
 */
public class GenericHeader implements Header {
    /** The header name. */
    private String name;

    /** The header value. */
    private String value;

    /**
     * Constructs a new header with the provided name and value.
     *
     * @param name
     *            The header name.
     * @param value
     *            The header value.
     */
    public GenericHeader(String name, String value) {
        this.name = value;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return value;
    }
}
