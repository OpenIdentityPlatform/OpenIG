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
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

/**
 * Manages a collection of associated objects created and initialized by {@link Heaplet}
 * objects. A heap object may be lazily initialized, meaning that it or its dependencies
 * may not be created until first requested from the heap.
 */
public interface Heap {

    /**
     * Returns an object from the heap with a specified name, or {@code null} if no such object exists.
     *
     * @param name
     *         the name of the object in the heap to be retrieved.
     * @param type
     *         expected type of the heap object
     * @param <T>
     *         expected type of the heap object
     * @return the requested object from the heap, or {@code null} if no such object exists.
     * @throws HeapException
     *         if an exception occurred during creation of the heap object or any of its dependencies.
     * @throws org.forgerock.json.fluent.JsonValueException
     *         if a heaplet (or one of its dependencies) has a malformed configuration object.
     */
    <T> T get(String name, Class<T> type) throws HeapException;

    /**
     * Resolves a mandatory object with the specified reference. If the object does not exist or the inline
     * declaration cannot be build, a {@link JsonValueException} is thrown. If the reference is an inline object
     * declaration, an anonymous object is added to the heap and returned.
     * <p>
     * Equivalent to:
     * <pre>
     *     heap.resolve(reference, type, false);
     * </pre>
     *
     * @param reference
     *         a JSON value containing the name of the heap object to retrieve.
     * @param type
     *         the expected type of the heap object.
     * @param <T>
     *         expected instance type
     * @return the specified heap object.
     * @throws HeapException
     *         if there was an exception creating the heap object or any of its dependencies.
     * @throws JsonValueException
     *         if the name contains {@code null}, is not a string, or the specified heap object could not be retrieved
     *         or has the wrong type or the reference is not a valid inline declaration.
     */
    <T> T resolve(JsonValue reference, Class<T> type) throws HeapException;

    /**
     * Resolves an object with the specified reference, optionally or not. If the reference is an inline object
     * declaration, an anonymous object is added to the heap and returned. If the inline declaration cannot be build, a
     * {@link JsonValueException} is thrown.
     *
     * @param reference
     *         a JSON value containing either the name of the heap object to retrieve or an inline declaration.
     * @param type
     *         the expected type of the heap object.
     * @param optional
     *         Accept or not a JsonValue that contains {@literal null}.
     * @param <T>
     *         expected instance type
     * @return the referenced heap object or {@code null} if name contains {@code null}.
     * @throws HeapException
     *         if there was an exception creating the heap object or any of its dependencies.
     * @throws JsonValueException
     *         if the reference is not a string, or the specified heap object has the wrong type or the reference is not
     *         a valid inline declaration.
     */
    <T> T resolve(JsonValue reference, Class<T> type, boolean optional) throws HeapException;
}
