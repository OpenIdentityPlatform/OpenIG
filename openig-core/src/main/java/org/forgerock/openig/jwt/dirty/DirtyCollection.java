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

import java.util.Collection;
import java.util.Iterator;

/**
 * A {@link Collection} decorator that notifies the provided {@link DirtyListener} when one ore more elements are
 * removed.
 * @param <E> type of the collection
 */
public class DirtyCollection<E> implements Collection<E> {
    private final Collection<E> delegate;
    private final DirtyListener listener;

    /**
     * Builds a new DirtyCollection delegating to the given {@literal Collection} and notifying the provided observer.
     *
     * @param delegate
     *         Collection delegate
     * @param listener
     *         change observer
     */
    public DirtyCollection(final Collection<E> delegate, final DirtyListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new DirtyIterator<E>(delegate.iterator(), listener);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return delegate.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        if (delegate.remove(o)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        if (delegate.removeAll(c)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        if (delegate.retainAll(c)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        delegate.clear();
        listener.onElementsRemoved();
    }

    @Override
    public boolean equals(final Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
