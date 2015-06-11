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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.jwt.dirty;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.forgerock.http.util.SetDecorator;

/**
 * A {@link Set} decorator that notifies the provided {@link DirtyListener} when one ore more elements are removed.
 * @param <E> type of the set
 */
public class DirtySet<E> extends SetDecorator<E> {

    private final DirtyListener listener;

    /**
     * Constructs a new set decorator, wrapping the specified set.
     *
     * @param set
     *         the set to wrap with the decorator.
     * @param listener
     *         the change observer
     */
    public DirtySet(final Set<E> set, final DirtyListener listener) {
        super(set);
        this.listener = listener;
    }

    @Override
    public Iterator<E> iterator() {
        return new DirtyIterator<>(super.iterator(), listener);
    }

    @Override
    public boolean remove(final Object o) {
        if (super.remove(o)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        if (super.removeAll(c)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        if (super.retainAll(c)) {
            listener.onElementsRemoved();
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        listener.onElementsRemoved();
    }
}
