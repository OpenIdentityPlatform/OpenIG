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

package org.forgerock.openig.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A {@link FieldMap} that can be extended with arbitrary keys. If the key maps
 * to a key exposed by the field map, the field map is used, otherwise the key
 * is handled in this implementation. The backing map is a {@link HashMap} with
 * default initial capacity and load factor.
 */
public class ExtensibleFieldMap extends AbstractMap<String, Object> implements Map<String, Object> {

    /** Map to store fields. */
    private final FieldMap fields;

    /** Map to store extended keys. */
    private final Map<String, Object> extension = new HashMap<String, Object>();

    /**
     * Constructs a new extensible field map, using this object's field members
     * as keys. This is only useful in the case where a class subclasses
     * {@code ExtensibleFieldMap}.
     */
    public ExtensibleFieldMap() {
        fields = new FieldMap(this);
    }

    /** The Map entrySet view. */
    private final Set<Entry<String, Object>> entrySet = new AbstractSet<Entry<String, Object>>() {
        @Override
        public void clear() {
            ExtensibleFieldMap.this.clear();
        }

        @Override
        public boolean contains(final Object o) {
            return (o instanceof Entry)
                    && ExtensibleFieldMap.this.containsKey(((Entry<?, ?>) o).getKey());
        }

        @Override
        public boolean isEmpty() {
            return ExtensibleFieldMap.this.isEmpty();
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new Iterator<Entry<String, Object>>() {
                final Iterator<Entry<String, Object>> fieldIterator = fields.entrySet().iterator();
                final Iterator<Entry<String, Object>> extensionIterator = extension.entrySet()
                        .iterator();
                Iterator<Entry<String, Object>> currentIterator = fieldIterator;

                @Override
                public boolean hasNext() {
                    return fieldIterator.hasNext() || extensionIterator.hasNext();
                }

                @Override
                public Entry<String, Object> next() {
                    if (!currentIterator.hasNext() && currentIterator != extensionIterator) {
                        currentIterator = extensionIterator;
                    }
                    return currentIterator.next();
                }

                @Override
                public void remove() {
                    currentIterator.remove();
                }
            };
        }

        @Override
        public boolean remove(final Object o) {
            return (o instanceof Entry)
                    && ExtensibleFieldMap.this.remove(((Entry<?, ?>) o).getKey()) != null;
        }

        @Override
        public int size() {
            return ExtensibleFieldMap.this.size();
        }
    };

    /**
     * Constructs a new extensible field map, using the specified object's field
     * members as keys.
     *
     * @param object the object whose field members are to be exposed in the map.
     */
    public ExtensibleFieldMap(Object object) {
        fields = new FieldMap(object);
    }

    @Override
    public Object get(Object key) {
        return fields.containsKey(key) ? fields.get(key) : extension.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return fields.containsKey(key) || extension.containsKey(key);
    }

    @Override
    public Object remove(Object key) {
        return fields.containsKey(key) ? fields.remove(key) : extension.remove(key);
    }

    @Override
    public void clear() {
        fields.clear();
        extension.clear();
    }

    @Override
    public int size() {
        return fields.size() + extension.size();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return entrySet;
    }

    @Override
    public boolean isEmpty() {
        return fields.isEmpty() && extension.isEmpty();
    }

    @Override
    public Object put(String key, Object value) {
        return fields.containsKey(key) ? fields.put(key, value) : extension.put(key, value);
    }
}
