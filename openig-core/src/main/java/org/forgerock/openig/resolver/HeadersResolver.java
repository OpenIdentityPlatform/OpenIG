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
 * Copyright 2015 ForgeRock AS.
 *
 */

package org.forgerock.openig.resolver;

import org.forgerock.http.protocol.Headers;

/**
 * Resolves {@link Headers} objects.
 */
public class HeadersResolver implements Resolver {

    @Override
    public Class<?> getKey() {
        return Headers.class;
    }

    @Override
    public Object get(Object object, Object element) {
        if (object instanceof Headers) {
            Headers headers = (Headers) object;
            String key = element.toString();
            if (headers.containsKey(key)) {
                // We just do the getValues() on behalf of the script so we don't have to write .values in expressions
                return headers.get(key).getValues();
            }
        }
        return Resolver.UNRESOLVED;
    }

    @Override
    public Object put(Object object, Object element, Object value) {
        if (object instanceof Headers) {
            Headers headers = (Headers) object;
            String key = element.toString();
            return headers.put(key, value);
        }
        return Resolver.UNRESOLVED;
    }
}
