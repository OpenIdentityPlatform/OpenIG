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

import static java.lang.String.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.forgerock.openig.decoration.global.GlobalDecorator.*;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.util.Json.*;
import static org.forgerock.util.Reject.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.decoration.global.GlobalDecorator;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.MultiValueMap;

/**
 * The concrete implementation of a heap. Provides methods to initialize and destroy a heap.
 * A Heap can be part of a heap hierarchy: if the queried object is not found locally, and if it has a parent,
 * the parent will be queried (and this, recursively until there is no parent anymore).
 */
public class HeapImpl implements Heap {

    /**
     * List of attributes that should be ignored when looking for decorator's configuration.
     */
    private static final List<String> EXCLUDED_ATTRIBUTES = asList("type", "name", "config");

    /**
     * Parent heap to delegate queries to if nothing is found in the local heap.
     * It may be null if this is the root heap (built by the system).
     */
    private final Heap parent;

    /**
     * Heap name.
     */
    private final Name name;

    /** Heaplets mapped to heaplet identifiers in the heap configuration. */
    private Map<String, Heaplet> heaplets = new HashMap<String, Heaplet>();

    /** Configuration objects for heaplets. */
    private Map<String, JsonValue> configs = new HashMap<String, JsonValue>();

    /** Objects allocated in the heap mapped to heaplet names. */
    private Map<String, Object> objects = new HashMap<String, Object>();

    /** Per-heaplet decoration(s) mapped to heaplet names. */
    private MultiValueMap<String, JsonValue> decorations =
            new MultiValueMap<String, JsonValue>(new LinkedHashMap<String, List<JsonValue>>());

    /**
     * Builds an anonymous root heap (will be referenced by children but has no parent itself).
     * Intended for tests only.
     */
    HeapImpl() {
        this((Heap) null);
    }

    /**
     * Builds a new anonymous heap that is a child of the given heap.
     * Intended for tests only.
     *
     * @param parent
     *         parent heap.
     */
    HeapImpl(final Heap parent) {
        this(parent, Name.of("anonymous"));
    }

    /**
     * Builds a root heap (will be referenced by children but has no parent itself).
     *
     * @param name
     *         local name of this heap
     */
    public HeapImpl(final Name name) {
        this(null, name);
    }

    /**
     * Builds a new heap that is a child of the given heap.
     *
     * @param parent
     *         parent heap.
     * @param name
     *         local name of this heap
     */
    public HeapImpl(final Heap parent, final Name name) {
        this.parent = parent;
        this.name = checkNotNull(name);
    }

    /**
     * Initializes the heap using the given configuration. Once complete, all heaplets will
     * be loaded and all associated objects are allocated using each heaplet instance's
     * configuration.
     *
     * @param config the configuration root.
     * @param reservedFieldNames the names of reserved top level fields in the config which
     *                           should not be parsed as global decorators.
     * @throws HeapException if an exception occurs allocating heaplets.
     * @throws JsonValueException if the configuration object is malformed.
     */
    public synchronized void init(JsonValue config, String... reservedFieldNames)
            throws HeapException {
        // process configuration object model structure
        boolean logDeprecationWarning = false;
        JsonValue heap = config.get("heap").defaultTo(emptyList());
        if (heap.isMap()) {
            /*
             * In OpenIG < 3.1 the heap objects were listed in a child "objects"
             * array. The extra nesting was found to be redundant and removed in
             * 3.1. We continue to allow it in order to maintain backwards
             * compatibility.
             */
            heap = heap.get("objects").required();

            // We cannot log anything just yet because the heap is not initialized.
            logDeprecationWarning = true;
        }
        for (JsonValue object : heap.expect(List.class)) {
            addDeclaration(object);
        }

        // register global decorators, ensuring that reserved field names are filtered out
        int sz = reservedFieldNames.length;
        String[] allReservedFieldNames = Arrays.copyOf(reservedFieldNames, sz + 1);
        allReservedFieldNames[sz] = "heap";
        Decorator parentGlobalDecorator =
                parent != null ? parent.get(GLOBAL_DECORATOR_HEAP_KEY, Decorator.class) : null;
        GlobalDecorator globalDecorator = new GlobalDecorator(parentGlobalDecorator, config, allReservedFieldNames);
        put(GLOBAL_DECORATOR_HEAP_KEY, globalDecorator);

        // instantiate all objects, recursively allocating dependencies
        for (String name : new ArrayList<String>(heaplets.keySet())) {
            get(name, Object.class);
        }

        // We can log a warning now that the heap is initialized.
        if (logDeprecationWarning) {
            Logger logger =
                    new Logger(resolve(config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                            LogSink.class, true), name);
            logger.warning("The configuration field heap/objects has been deprecated. Heap objects "
                    + "should now be listed directly in the top level \"heap\" field, "
                    + "e.g. { \"heap\" : [ objects... ] }.");
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
    public void addDeclaration(final JsonValue object) {
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
        // Store decorations
        for (JsonValue candidate : object) {
            // Exclude standard declaration elements
            if (!EXCLUDED_ATTRIBUTES.contains(candidate.getPointer().leaf())) {
                decorations.add(name, candidate);
            }
        }
    }

    @Override
    public synchronized <T> T get(final String name, final Class<T> type) throws HeapException {
        Object object = objects.get(name);
        if (object == null) {
            Heaplet heaplet = heaplets.get(name);
            if (heaplet != null) {
                object = heaplet.create(this.name.child(name), configs.get(name), this);
                if (object == null) {
                    throw new HeapException(new NullPointerException());
                }
                object = decorate(name, object);
                put(name, object);
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
            String generated = name(required);

            // when resolve() is called multiple times with the same reference, this prevent "already registered" errors
            T value = get(generated, type);
            if (value == null) {
                // First resolution
                required.put("name", generated);
                addDeclaration(required);
                // Get decorated object
                value = get(generated, type);
                if (value == null) {
                    // Very unlikely to happen
                    throw new JsonValueException(reference, "Reference is not a valid heap object");
                }
            }
            return value;
        }
        throw new JsonValueException(reference,
                                     format("JsonValue[%s] is neither a String nor a Map (inline declaration)",
                                            reference.getPointer()));
    }

    /**
     * Infer a locally unique name for the given object declaration.
     * If a {@literal name} attribute is provided, simply return its value as name, otherwise compose a
     * unique name composed of both the declaration JSON pointer (map to the location within the JSON file) and
     * the value of the {@literal type} attribute (ease to identify the object).
     * <p>
     * The following declaration would return {@literal Inline}:
     * <pre>
     *     {@code
     *     {
     *         "name": "Inline",
     *         "type": "Router"
     *     }
     *     }
     * </pre>
     * <p>
     * And this one would return {@literal {WelcomeHandler}/heap/objects/0/config/defaultHandler}:
     * <pre>
     *     {@code
     *     {
     *         "type": "WelcomeHandler"
     *     }
     *     }
     * </pre>
     * @param declaration source inline object declaration
     * @return a locally unique name
     */
    public static String name(final JsonValue declaration) {
        JsonValue node = declaration.get("name");
        if (node.isNull()) {
            String location = declaration.getPointer().toString();
            String type = declaration.get("type").required().asString();
            return format("{%s}%s", type, location);
        }
        return node.asString();
    }

    /**
     * Puts an object into the heap. If an object already exists in the heap with the
     * specified name, it is overwritten.
     *
     * @param name name of the object to be put into the heap.
     * @param object the object to be put into the heap.
     */
    public synchronized void put(final String name, final Object object) {
        objects.put(name, object);
    }

    /**
     * Decorates the given heap object.
     *
     * @param name
     *         heap object name
     * @param object
     *         heap object instance
     * @return the decorated object or the original one if no decorator were applied.
     * @throws HeapException
     *         if a decorator failed to apply
     */
    private Object decorate(final String name, final Object object) throws HeapException {

        // Avoid decorating decorators themselves
        // Avoid StackOverFlow Exceptions because of infinite recursion
        if (object instanceof Decorator) {
            return object;
        }

        // Starts with the original object
        Object decorated = object;
        // Create a context object for holding shared values
        Context context = new DecorationContext(this,
                                                this.name.child(name),
                                                configs.get(name).defaultTo(emptyMap()));

        // Apply global decorations (may be inherited from parent heap)
        Decorator globalDecorator = get(GLOBAL_DECORATOR_HEAP_KEY, Decorator.class);
        if (globalDecorator != null) {
            decorated = globalDecorator.decorate(decorated, null, context);
        }

        if (decorations.containsKey(name)) {
            // We have decorators for this instance, try to apply them
            for (JsonValue decoration : decorations.get(name)) {

                // The element name is the decorator heap object name
                String decoratorName = decoration.getPointer().leaf();
                Decorator decorator = get(decoratorName, Decorator.class);
                if (decorator != null) {
                    // We just ignore when no named decorator is found
                    // TODO Keep a list of intermediate objects for later use
                    decorated = decorator.decorate(decorated, decoration, context);
                }
            }
        }
        return decorated;
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

    /**
     * A simple data holder object letting decorators known about the decorated heap object.
     */
    private class DecorationContext implements Context {
        private final Heap heap;
        private final Name name;
        private final JsonValue config;

        public DecorationContext(final Heap heap,
                                 final Name name,
                                 final JsonValue config) {
            this.heap = heap;
            this.name = name;
            this.config = config;
        }

        @Override
        public Heap getHeap() {
            return heap;
        }

        @Override
        public Name getName() {
            return name;
        }

        @Override
        public JsonValue getConfig() {
            return config;
        }
    }
}
