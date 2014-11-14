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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.helper;

import static org.forgerock.util.Reject.*;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;

/**
 * Lazily resolve a {@link JsonValue} reference node against a provided {@link Heap} instance.
 * Once the reference has been acquired, no other resolution is tried (except for optional references resolved
 * to {@code null}).
 * @param <T> expected type of the resolved reference object
 */
public final class LazyReference<T> {

    /**
     * Builds a LazyReference dedicated to resolve the given (optional or not) {@code reference} of type {@code type}
     * from the given {@code heap}.
     *
     * @param heap
     *         Heap instance that will try to resolve the reference
     * @param reference
     *         Reference to be resolved (can be an inline declaration)
     * @param type
     *         expected resolved type of the reference
     * @param optional
     *         is this reference optional (return {@code null} if the given {@code reference} wraps a {@code null}
     *         value)
     * @return  a new LazyReference
     * @param <R> expected resolved type of the reference
     */
    public static <R> LazyReference<R> newReference(final Heap heap,
                                                    final JsonValue reference,
                                                    final Class<R> type,
                                                    final boolean optional) {
        return new LazyReference<R>(checkNotNull(heap), checkNotNull(reference), type, optional);
    }

    private final Heap heap;
    private final JsonValue reference;
    private final Class<T> type;
    private final boolean optional;

    private T resolved;

    private LazyReference(final Heap heap, final JsonValue reference, final Class<T> type, final boolean optional) {
        this.heap = heap;
        this.reference = reference;
        this.type = type;
        this.optional = optional;
    }

    /**
     * Resolves the encapsulated reference.
     * Notice that synchronization is done in the Heap, so no need to cover that here.
     *
     * @return the resolved instance, or {@code null} if it was optional and not set.
     * @throws HeapException
     *         if resolution failed, this error is the one thrown be the heap, untouched.
     */
    public T get() throws HeapException {
        if (resolved == null) {
            resolved = heap.resolve(reference, type, optional);
        }
        return resolved;
    }

}
