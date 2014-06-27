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

package org.forgerock.openig.heap;

// TODO: consider detecting cyclic dependencies

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.util.JsonValueUtil;

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
    private HashMap<String, Heaplet> heaplets = new HashMap<String, Heaplet>();

    /** Configuration objects for heaplets. */
    private HashMap<String, JsonValue> configs = new HashMap<String, JsonValue>();

    /** Objects allocated in the heap mapped to heaplet names. */
    private HashMap<String, Object> objects = new HashMap<String, Object>();

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
            object.required().expect(Map.class);
            Heaplet heaplet = Heaplets.getHeaplet(JsonValueUtil.asClass(object.get("type").required()));
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
            configs.put(name, object.get("config").required().expect(Map.class));
        }
        // instantiate all objects, recursively allocating dependencies
        for (String name : heaplets.keySet()) {
            get(name);
        }
    }

    @Override
    public synchronized Object get(String name) throws HeapException {
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
                return parent.get(name);
            }
        }
        return object;
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
        HashMap<String, Heaplet> h = heaplets;
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
