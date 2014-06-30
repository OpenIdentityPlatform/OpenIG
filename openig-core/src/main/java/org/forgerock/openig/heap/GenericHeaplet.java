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

import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;

/**
 * A generic base class for heaplets with automatically injected fields.
 * <p>
 * If the object created is an instance of {@link GenericHeapObject}, it is then automatically injected with
 * {@code logger} and {@code storage} objects.
 */
public abstract class GenericHeaplet implements Heaplet {

    /** Heap objects to avoid dependency injection (prevents circular dependencies). */
    private static final Set<String> SPECIAL_OBJECTS =
            new HashSet<String>(Arrays.asList(LOGSINK_HEAP_KEY, TEMPORARY_STORAGE_HEAP_KEY));

    /** The name of the object to be created and stored in the heap by this heaplet. */
    protected String name;

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
    public abstract Class<?> getKey();

    @Override
    public Object create(String name, JsonValue config, Heap heap) throws HeapException {
        this.name = name;
        this.config = config.required().expect(Map.class);
        this.heap = heap;
        if (!SPECIAL_OBJECTS.contains(name)) {
            this.logger = new Logger(
                    HeapUtil.getObject(
                            heap,
                            config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                            LogSink.class),
                    name);
            this.storage = HeapUtil.getRequiredObject(
                    heap,
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
        return object;
    }

    @Override
    public void destroy() {
        // default does nothing
    }

    /**
     * Called to request the heaplet create an object. Called by {@link GenericHeaplet#create(String, JsonValue, Heap)}
     * after initializing the protected field members.
     *
     * @return The created object.
     * @throws HeapException
     *             if an exception occurred during creation of the heap object or any of its dependencies.
     * @throws JsonValueException
     *             if the heaplet (or one of its dependencies) has a malformed configuration.
     */
    public abstract Object create() throws HeapException;
}
