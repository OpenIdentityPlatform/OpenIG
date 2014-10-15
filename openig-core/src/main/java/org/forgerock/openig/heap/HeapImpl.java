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

// TODO: consider detecting cyclic dependencies
import static org.forgerock.openig.util.Json.*;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

/**
 * The concrete implementation of a heap. Provides methods to initialize and destroy a heap.
 * A Heap can be part of a heap hierarchy: if the queried object is not found locally, and if it has a parent,
 * the parent will be queried (and this, recursively until there is no parent anymore).
 */
public class HeapImpl implements Heap {

    /**
     * Parent heap to delegate queries to if nothing is found in the local heap.
     * It may be null if this is the root heap (built by the system).
     */
    private final Heap parent;

    /** Heaplets mapped to heaplet identifiers in the heap configuration. */
    private Map<String, Heaplet> heaplets = new HashMap<String, Heaplet>();

    /** Configuration objects for heaplets. */
    private Map<String, JsonValue> configs = new HashMap<String, JsonValue>();

    /** Objects allocated in the heap mapped to heaplet names. */
    private Map<String, Object> objects = new HashMap<String, Object>();

    /**
     * Builds a root heap (will be referenced by children but has no parent itself).
     */
    public HeapImpl() {
        this(null);
    }

    /**
     * Builds a new heap that is a child of the given heap.
     * @param parent parent heap.
     */
    public HeapImpl(final Heap parent) {
        this.parent = parent;
    }

    /**
     * Initializes the heap using the given configuration. Once complete, all heaplets will
     * be loaded and all associated objects are allocated using each heaplet instance's
     * configuration.
     *
     * @param config a heap configuration object tree containing the heap configuration.
     * @throws HeapException if an exception occurs allocating heaplets.
     * @throws JsonValueException if the configuration object is malformed.
     */
    public synchronized void init(JsonValue config) throws HeapException {
        // process configuration object model structure
        for (JsonValue object : config.get("objects").required().expect(List.class)) {
            addDeclaration(object);
        }
        // instantiate all objects, recursively allocating dependencies
        for (String name : new ArrayList<String>(heaplets.keySet())) {
            get(name, Object.class);
        }
    }

    /**
     * Add the given JsonValue as a new object declaration in this heap. The given object must be a valid object
     * declaration ({@literal name}, {@literal type} and {@literal config} attributes). If not, a JsonValueException
     * will be thrown. After this method is called, a new object is available in the heap.
     *
     * <p>The {@literal config} attribute is optional and accordingly, if empty or null, its declaration can be omitted.
     *
     * @param object
     *         object declaration to add to the heap.
     */
    private void addDeclaration(final JsonValue object) {
        object.required().expect(Map.class);
        Heaplet heaplet = Heaplets.getHeaplet(asClass(object.get("type").required()));
        if (heaplet == null) {
            throw new JsonValueException(object.get("type"), "no heaplet available to initialize object");
        }
        // objects[n].name (string)
        String name = object.get("name").required().asString();
        if (heaplets.get(name) != null) {
            throw new JsonValueException(object.get("name"), "object already defined");
        }
        // remove pre-allocated objects to be replaced
        objects.remove(name);
        heaplets.put(name, heaplet);
        // objects[n].config (object)
        configs.put(name, object.get("config").defaultTo(emptyMap()).expect(Map.class));
    }

    @Override
    public synchronized <T> T get(final String name, final Class<T> type) throws HeapException {
        Object object = objects.get(name);
        if (object == null) {
            Heaplet heaplet = heaplets.get(name);
            if (heaplet != null) {
                object = heaplet.create(name, configs.get(name), this);
                if (object == null) {
                    throw new HeapException(new NullPointerException());
                }
                objects.put(name, object);
            } else if (parent != null) {
                // no heaplet available, query parent (if any)
                return parent.get(name, type);
            }
        }
        return type.cast(object);
    }

    @Override
    public <T> T resolve(final JsonValue reference, final Class<T> type) throws HeapException {
        return resolve(reference, type, false);
    }

    @Override
    public <T> T resolve(final JsonValue reference, final Class<T> type, final boolean optional) throws HeapException {

        // If optional if set, accept that the provided reference may wrap a null
        if (optional && reference.isNull()) {
            return null;
        }

        // Otherwise we require a value
        JsonValue required = reference.required();
        if (required.isString()) {
            // handle named reference
            T value = get(required.asString(), type);
            if (value == null) {
                throw new JsonValueException(reference, "Object " + reference.asString() + " not found in heap");
            }
            return value;
        } else if (required.isMap()) {
            // handle inline declaration
            String generated = required.getPointer().toString();
            required.put("name", generated);
            addDeclaration(required);
            T value = get(generated, type);
            if (value == null) {
                throw new JsonValueException(reference, "Reference is not a valid heap object");
            }
            return value;
        }
        throw new JsonValueException(reference,
                                     format("JsonValue[%s] is neither a String nor a Map (inline declaration)",
                                            reference.getPointer()));
    }

    /**
     * Puts an object into the heap. If an object already exists in the heap with the
     * specified name, it is overwritten.
     *
     * @param name name of the object to be put into the heap.
     * @param object the object to be put into the heap.
     */
    public synchronized void put(String name, Object object) {
        objects.put(name, object);
    }

    /**
     * Destroys the objects on the heap and dereferences all associated objects. This method
     * calls the heaplet {@code destroy} method for each object in the heap to provide a
     * chance for system resources to be freed.
     */
    public synchronized void destroy() {
        // save the heaplets locally to send destroy notifications
        Map<String, Heaplet> h = heaplets;
        // prevent any further (inadvertent) object allocations
        heaplets = new HashMap<String, Heaplet>();
        // all allocated objects are no longer in this heap
        objects.clear();
        // iterate through saved heaplets, notifying about destruction
        for (String name : h.keySet()) {
            h.get(name).destroy();
        }
    }
}
