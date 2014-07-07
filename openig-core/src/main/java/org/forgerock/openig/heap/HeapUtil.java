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

package org.forgerock.openig.heap;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;


/**
 * Utility methods for managing objects in the heap.
 */
public final class HeapUtil {

    /** Static methods only. */
    private HeapUtil() {
    }

    /**
     * Retreives an object from a heap with the specified name and type.
     *
     * @param heap the heap to retrieve the object from.
     * @param name a JSON value containing the name of the heap object to retrieve.
     * @param type the expected type of the heap object.
     * @param <T> expected instance type
     * @return the specified heap object.
     * @throws HeapException if there was an exception creating the heap object or any of its dependencies.
     * @throws JsonValueException if the name contains {@code null}, is not a string, or the specified heap object
     * has the wrong type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getObject(Heap heap, JsonValue name, Class<T> type) throws HeapException {
        Object o = heap.get(name.required().asString());
        if (o != null && !(type.isInstance(o))) {
            throw new JsonValueException(name, "expecting heap object of type " + type.getName());
        }
        return (T) o;
    }

    /**
     * Retreives an object from a heap with the specified name and type. If the object does not
     * exist, a {@link JsonValueException} is thrown.
     *
     * @param heap the heap to retrieve the object from.
     * @param name a JSON value containing the name of the heap object to retrieve.
     * @param type the expected type of the heap object.
     * @param <T> expected instance type
     * @return the specified heap object.
     * @throws HeapException if there was an exception creating the heap object or any of its dependencies.
     * @throws JsonValueException if the name contains {@code null}, is not a string, or the specified heap object
     * could not be retrieved or has the wrong type.
     */
    public static <T> T getRequiredObject(Heap heap, JsonValue name, Class<T> type) throws HeapException {
        T t = getObject(heap, name, type);
        if (t == null) {
            throw new JsonValueException(name, "object " + name.asString() + " not found in heap");
        }
        return t;
    }
}
