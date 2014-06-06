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
 * Portions Copyrighted 2011 ForgeRock AS.
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
 */
public class HeapImpl implements Heap {

    /** Heaplets mapped to heaplet identifiers in the heap configuration. */
    private HashMap<String, Heaplet> heaplets = new HashMap<String, Heaplet>();

    /** Configuration objects for heaplets. */
    private HashMap<String, JsonValue> configs = new HashMap<String, JsonValue>();

    /** Objects allocated in the heap mapped to heaplet names. */
    private HashMap<String, Object> objects = new HashMap<String, Object>();

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
            String name = object.get("name").required().asString(); // objects[n].name (string)
            if (heaplets.get(name) != null) {
                throw new JsonValueException(object.get("name"), "object already defined");
            }
            objects.remove(name); // remove pre-allocated objects to be replaced
            heaplets.put(name, heaplet);
            configs.put(name, object.get("config").required().expect(Map.class)); // objects[n].config (object)
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
        HashMap<String, Heaplet> h = heaplets; // save the heaplets locally to send destroy notifications
        heaplets = new HashMap<String, Heaplet>(); // prevent any further (inadvertent) object allocations
        objects.clear(); // all allocated objects are no longer in this heap
        for (String name : h.keySet()) { // iterate through saved heaplets, notifying about destruction
            h.get(name).destroy();
        }
    }
}
