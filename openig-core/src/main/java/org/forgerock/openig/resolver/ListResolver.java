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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

import java.util.List;

import org.forgerock.http.util.UnmodifiableCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@link List} objects.
 */
@SuppressWarnings("rawtypes")
public class ListResolver implements Resolver {

    private static final Logger logger = LoggerFactory.getLogger(ListResolver.class);

    @Override
    public Class<?> getKey() {
        return List.class;
    }

    @Override
    public Object get(Object object, Object element) {
        if (object instanceof List && element instanceof Number) {
            try {
                return ((List) object).get(((Number) element).intValue());
            } catch (IndexOutOfBoundsException ioobe) {
                logger.warn("An error occurred during the resolution", ioobe);
                // cannot resolve index
            }
        }
        return Resolver.UNRESOLVED;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object put(Object object, Object element, Object value) {
        if (object instanceof List && !(object instanceof UnmodifiableCollection)
                && element instanceof Number) {
            List list = (List) object;
            int index = ((Number) element).intValue();
            try {
                if (list.size() > index) {
                    // within existing list
                    list.set(index, value);
                } else if (list.size() == index) {
                    // appending to end of list
                    list.add(element);
                }
                // otherwise, ignore out-of-range index
            } catch (UnsupportedOperationException uoe) {
                logger.warn("Can't insert an element into a read-only list", uoe);
                // ignore failed attempts to write to read-only list
            }
        }
        return Resolver.UNRESOLVED;
    }
}
