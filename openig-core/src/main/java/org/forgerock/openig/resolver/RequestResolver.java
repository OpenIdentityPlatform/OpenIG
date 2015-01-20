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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.http.Request;

/**
 * Resolves {@link Request} objects.
 */
public class RequestResolver extends BeanResolver {

    @Override
    public Object put(Object object, Object element, Object value) {
        if (object instanceof Request) {
            final Request request = (Request) object;
            if (element instanceof String && "uri".equals(element)) {
                if (value instanceof String) {
                    try {
                        return request.setUri((String) value);
                    } catch (URISyntaxException ex) {
                        // Cannot resolve the value as a URI.
                    }
                } else if (value instanceof URI) {
                    return request.setUri((URI) value);
                }
            }
        }
        return super.put(object, element, value);
    }
}
