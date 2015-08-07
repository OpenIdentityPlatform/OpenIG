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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;

/**
 * A generic base class for heaplets with automatically injected fields.
 * <p>
 * If the object created is an instance of {@link GenericHeapObject}, it is then
 * automatically injected with {@code logger} and {@code storage} objects.
 */
public abstract class GenericHeaplet implements Heaplet {

    /** Heap objects to avoid dependency injection (prevents circular dependencies). */
    private static final Set<String> SPECIAL_OBJECTS =
            new HashSet<>(Arrays.asList(LOGSINK_HEAP_KEY, TEMPORARY_STORAGE_HEAP_KEY));

    /** The name of the object to be created and stored in the heap by this heaplet. */
    protected String name;

    /** The fully qualified name of the object to be created. */
    protected Name qualified;

    /** The heaplet's object configuration object. */
    protected JsonValue config;

    /** Where objects should be put and where object dependencies should be retrieved. */
    protected Heap heap;

    /** Provides methods for logging activities. */
    protected Logger logger;

    /** Allocates temporary buffers for caching streamed content during processing. */
    protected TemporaryStorage storage;

    /** The object created by the heaplet's {@link #create()} method. */
    protected Object object;

    @Override
    public Object create(Name name, JsonValue config, Heap heap) throws HeapException {
        this.name = name.getLeaf();
        this.qualified = name;
        this.config = config.required().expect(Map.class);
        this.heap = heap;
        if (!SPECIAL_OBJECTS.contains(this.name)) {
            this.logger = new Logger(
                    heap.resolve(
                            config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                            LogSink.class, true),
                    name);
            this.storage = heap.resolve(
                    config.get("temporaryStorage").defaultTo(TEMPORARY_STORAGE_HEAP_KEY),
                    TemporaryStorage.class);
        }
        this.object = create();
        if (this.object instanceof GenericHeapObject) {
            // instrument object if possible
            GenericHeapObject ghObject = (GenericHeapObject) this.object;
            ghObject.logger = this.logger;
            ghObject.storage = this.storage;
        }
        start();
        return object;
    }

    @Override
    public void destroy() {
        // default does nothing
    }

    /**
     * Called to request the heaplet create an object. Called by
     * {@link Heaplet#create(Name, JsonValue, Heap)} after initializing
     * the protected field members. Implementations should parse configuration
     * but not acquire resources, start threads, or log any initialization
     * messages. These tasks should be performed by the {@link #start()} method.
     *
     * @return The created object.
     * @throws HeapException
     *             if an exception occurred during creation of the heap object
     *             or any of its dependencies.
     * @throws org.forgerock.json.JsonValueException
     *             if the heaplet (or one of its dependencies) has a malformed
     *             configuration.
     */
    public abstract Object create() throws HeapException;

    /**
     * Called to request the heaplet start an object. Called by
     * {@link Heaplet#create(Name, JsonValue, Heap)} after creating and
     * configuring the object and once the object's logger and storage have been
     * configured. Implementations should override this method if they need to
     * acquire resources, start threads, or log any initialization messages.
     *
     * @throws HeapException
     *             if an exception occurred while starting the heap object or
     *             any of its dependencies.
     */
    public void start() throws HeapException {
        // default does nothing
    }
}
