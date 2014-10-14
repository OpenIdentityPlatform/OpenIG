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

package org.forgerock.util.functional;

import java.util.Arrays;
import java.util.Collections;

public final class Lists {

    public static interface List<T> {

        public abstract T head();

        public abstract List<T> tail();

        public abstract boolean isEmpty();
    }

    public static final class NonEmptyList<T> implements List<T> {

        private final T head;
        private final List<T> tail;

        private NonEmptyList(T head, List<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        public T head() {
            return head;
        }

        public List<T> tail() {
            return tail;
        }

        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            List<?> that = (List<?>) other;
            return head().equals(that.head()) && tail().equals(that.tail());
        }

        @Override
        public int hashCode() {
            return 37 * (head().hashCode() + tail().hashCode());
        }

        @Override
        public String toString() {
            return "(" + head() + ", " + tail() + ")";
        }
    }

    public static class EmptyListHasNoHead extends RuntimeException {}

    public static class EmptyListHasNoTail extends RuntimeException {}

    public static final List<?> EMPTY = new List<Object>() {

        public Object head() {
            throw new EmptyListHasNoHead();
        }

        public List<Object> tail() {
            throw new EmptyListHasNoTail();
        }

        public boolean isEmpty() {
            return true;
        }

        @Override
        public String toString() {
            return "()";
        }
    };

    @SuppressWarnings(value = "unchecked")
    public static <T> List<T> emptyList() {
        return (List<T>) EMPTY;
    }

    public static <T> List<T> list(T head, List<T> tail) {
        return new NonEmptyList<T>(head, tail);
    }

    public static <T> List<T> asList(java.util.List<T> list) {

        List<T> newList = emptyList();

        Collections.reverse(list);
        for (T item : list) {
            newList = new NonEmptyList<T>(item, newList);
        }

        return newList;
    }

    public static <T> List<T> asList(T... item) {
        return asList(Arrays.asList(item));
    }
}
