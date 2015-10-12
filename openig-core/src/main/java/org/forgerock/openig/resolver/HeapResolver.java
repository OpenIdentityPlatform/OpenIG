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
 */
package org.forgerock.openig.resolver;

import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;

/**
 * Resolves {@link Heap} objects.
 */
public class HeapResolver implements Resolver {

    @Override
    public Class<?> getKey() {
        return Heap.class;
    }

    @Override
    public Object get(Object object, Object element) {
        if (object instanceof Heap) {
            Heap heap = (Heap) object;
            String key = element.toString();
            try {
                return heap.get(key, Object.class);
            } catch (HeapException ex) {
                return Resolver.UNRESOLVED;
            }
        }
        return Resolver.UNRESOLVED;
    }

    @Override
    public Object put(Object object, Object element, Object value) {
        return Resolver.UNRESOLVED;
    }
}
