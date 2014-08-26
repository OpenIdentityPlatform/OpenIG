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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http;

import java.util.List;

import org.forgerock.http.util.CaseInsensitiveMap;
import org.forgerock.http.util.MultiValueMap;

/**
 * Message headers, a case-insensitive multiple-value map.
 */
public class Headers extends MultiValueMap<String, String> {
    /*
     * TODO: if this class implemented MultiValueMap<String, Header> then we
     * could lazily convert header values on demand. Subsequent changes to the
     * headers (assuming they are mutable) would then be reflected when the
     * headers are re-serialized. For example, a filter could modify the cookies
     * and have those changes automatically applied when the header is
     * re-encoded when it is forwarded. However, changing the type of map values
     * from String to Header may have implications on our EL and scripting
     * support. For example, previously it was possible to do
     * "request.headers.Connection = [ 'token1', 'token2' ]" in Groovy, but it
     * may not be possible with if the values are Headers.
     */

    /**
     * Constructs a new instance of message headers.
     */
    public Headers() {
        super(new CaseInsensitiveMap<List<String>>());
    }

    /**
     * Adds the provider header but only if it is non-empty.
     *
     * @param header
     *            The header.
     * @return This object.
     */
    public Headers putSingle(Header header) {
        // Only include the header if it is not empty.
        String value = header.toString();
        if (value != null && !value.isEmpty()) {
            putSingle(header.getName(), value);
        }
        return this;
    }
}
