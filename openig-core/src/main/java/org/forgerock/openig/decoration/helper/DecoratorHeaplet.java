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

package org.forgerock.openig.decoration.helper;

import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Heaplet;
import org.forgerock.openig.heap.Name;

/**
 * A base class for decorator heaplets.
 * <p>
 * It supports an additional {@link #start()} lifecycle method that is performed within the {@link #create
 * (Name, JsonValue, Heap)} method.
 * <p>
 * This class does not attempt to perform any heap object extraction/resolution when creating objects.
 */
public abstract class DecoratorHeaplet implements Heaplet {
    /** The fully qualified name of the object to be created. */
    protected Name name;
    /** The heaplet's object configuration object. */
    protected JsonValue config;
    /** Where objects should be put and where object dependencies should be retrieved. */
    protected Heap heap;
    /** The object created by the heaplet's {@link #create()} method. */
    protected Decorator object;

    /**
     * Can only be called by sub-classes.
     */
    protected DecoratorHeaplet() { }

    @Override
    public Object create(Name name, JsonValue config, Heap heap) throws HeapException {
        this.name = name;
        this.config = config.required().expect(Map.class);
        this.heap = heap;
        this.object = create();
        start();
        return object;
    }

    @Override
    public void destroy() {
        // default does nothing
    }

    /**
     * Called to request the heaplet create an object. Called by
     * {@link DecoratorHeaplet#create(Name, JsonValue, Heap)} after initializing
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
    public abstract Decorator create() throws HeapException;

    /**
     * Called to request the heaplet start an object. Called by
     * {@link DecoratorHeaplet#create(Name, JsonValue, Heap)} after creating and
     * configuring the object. Implementations should override this method if they need to
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
