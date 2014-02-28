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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Exposes public, non-primitive, non-static field members of an object as a
 * map. As fields cannot be removed from the underlying object, the
 * {@code remove} and {@code clear} methods merely set field values to
 * {@code null}.
 */
public class FieldMap extends AbstractMap<String, Object> implements Map<String, Object> {

    /**
     * Cache of field mappings to avoid overhead of repeated mapping via
     * reflection.
     */
    private static final HashMap<Class<?>, HashMap<String, Field>> MAPPINGS =
            new HashMap<Class<?>, HashMap<String, Field>>();

    private static HashMap<String, Field> getFields(final Object o) {
        final Class<?> c = o.getClass();
        HashMap<String, Field> fields = MAPPINGS.get(c);
        if (fields == null) { // lazy initialization
            fields = new HashMap<String, Field>();
            for (final Field f : c.getFields()) {
                final int modifiers = f.getModifiers();
                if (!f.isSynthetic() && !Modifier.isStatic(modifiers) && !f.isEnumConstant()
                        && !f.getType().isPrimitive()) {
                    fields.put(f.getName(), f);
                }
            }
            MAPPINGS.put(c, fields);
        }
        return fields;
    }

    /** The object whose field members are being exposed through the map. */
    private final Object object;

    /** Mapping between the map's keys and the object's fields. */
    private final HashMap<String, Field> fields;

    /**
     * Entry set view of this field map. Updates to the entry set write through
     * to the field map.
     */
    private final Set<Map.Entry<String, Object>> entrySet =
            new AbstractSet<Map.Entry<String, Object>>() {
                @Override
                public void clear() {
                    FieldMap.this.clear();
                }

                @Override
                public boolean contains(final Object o) {
                    return o instanceof Entry
                            && FieldMap.this.containsKey(((Entry<?, ?>) o).getKey());
                }

                @Override
                public boolean isEmpty() {
                    return fields.isEmpty();
                }

                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<Map.Entry<String, Object>>() {
                        private final Iterator<Map.Entry<String, Field>> iterator = fields
                                .entrySet().iterator();
                        private Map.Entry<String, Field> next;

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            next = iterator.next();
                            return new SimpleEntry<String, Object>(next.getKey(), getField(next
                                    .getValue())) {
                                private static final long serialVersionUID = -9059241192994713117L;

                                @Override
                                public Object setValue(final Object value) {
                                    putField(next.getValue(), value);
                                    return super.setValue(value);
                                }
                            };
                        }

                        @Override
                        public void remove() {
                            FieldMap.this.remove(next.getKey());
                        }
                    };
                }

                @Override
                public boolean remove(final Object o) {
                    return o instanceof Entry
                            && FieldMap.this.remove(((Entry<?, ?>) o).getKey()) != null;
                }

                @Override
                public int size() {
                    return fields.size();
                };

            };

    /**
     * Constructs a new extensible field map, using this object's field members
     * as keys. This is only useful in the case where a class subclasses
     * {@code FieldMap}.
     */
    public FieldMap() {
        this.object = this;
        this.fields = getFields(this);
    }

    /**
     * Constructs a new field map, using the specified object's field members as
     * keys.
     *
     * @param object
     *            the object whose field members are to be exposed in the map.
     */
    public FieldMap(final Object object) {
        this.object = object;
        this.fields = getFields(object);
    }

    /**
     * Sets the values of all fields to {@code null}.
     */
    @Override
    public void clear() {
        for (final String key : keySet()) {
            remove(key);
        }
    }

    /**
     * Returns {@code true} if the object contains the specified field name key.
     */
    @Override
    public boolean containsKey(final Object key) {
        return fields.containsKey(key);
    }

    /**
     * Returns an entry set view of this field map.
     */
    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return entrySet;
    }

    /**
     * Returns the value for the specified field name key.
     */
    @Override
    public Object get(final Object key) {
        return getField(fields.get(key));
    }

    @Override
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * Returns a {@link Set} of the field name keys.
     */
    @Override
    public Set<String> keySet() {
        return fields.keySet();
    }

    /**
     * Stores the specified value in the field with the specified field name
     * key.
     *
     * @throws UnsupportedOperationException
     *             if the specified field name key does not exist.
     */
    @Override
    public Object put(final String key, final Object value) {
        final Field field = fields.get(key);
        final Object old = getField(field);
        putField(field, value);
        return old;
    }

    /**
     * Sets the value of the field with the specified field name key to
     * {@code null}.
     *
     * @throws UnsupportedOperationException
     *             if the specified field name key does not exist.
     */
    @Override
    public Object remove(final Object key) {
        return key instanceof String ? put((String) key, null) : null;
    }

    /**
     * Returns the number of fields.
     */
    @Override
    public int size() {
        return fields.size();
    }

    private Object getField(final Field field) {
        try {
            return field != null ? field.get(object) : null;
        } catch (final IllegalAccessException iae) {
            throw new IllegalStateException(iae); // unexpected
        }
    }

    private void putField(final Field field, final Object value) {
        try {
            field.set(object, value);
        } catch (final Exception e) { // invalid field, invalid type or illegal access
            throw new UnsupportedOperationException(e);
        }
    }
}
