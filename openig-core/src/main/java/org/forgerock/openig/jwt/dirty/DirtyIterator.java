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

package org.forgerock.openig.jwt.dirty;

import java.util.Iterator;

/**
 * An {@link Iterator} decorator that notifies the provided {@link DirtyListener} when one element is removed.
 * @param <E> type of the iterator
 */
public class DirtyIterator<E> implements Iterator<E> {

    private final Iterator<E> delegate;
    private final DirtyListener listener;

    /**
     * Builds a new DirtyIterator delegating to the given {@literal Iterator} and notifying the provided observer.
     *
     * @param delegate
     *         Iterator delegate
     * @param listener
     *         change observer
     */
    public DirtyIterator(final Iterator<E> delegate, final DirtyListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public E next() {
        return delegate.next();
    }

    @Override
    public void remove() {
        delegate.remove();
        listener.onElementsRemoved();
    }
}
