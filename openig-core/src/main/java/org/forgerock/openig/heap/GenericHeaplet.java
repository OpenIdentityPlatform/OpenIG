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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.heap;

import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.EQUALS;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.openig.util.StringUtil.slug;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.util.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic base class for heaplets with automatically injected fields.
 * <p>
 * If the object created is an instance of {@link GenericHeapObject}, it is then
 * automatically injected with {@code logger} and {@code storage} objects.
 */
public abstract class GenericHeaplet implements Heaplet {

    private static final Logger logger = LoggerFactory.getLogger(GenericHeaplet.class);

    /** Heap objects to avoid dependency injection (prevents circular dependencies). */
    private static final Set<String> SPECIAL_OBJECTS =
            new HashSet<>(Arrays.asList(TEMPORARY_STORAGE_HEAP_KEY));

    /** The name of the object to be created and stored in the heap by this heaplet. */
    protected String name;

    /** The fully qualified name of the object to be created. */
    protected Name qualified;

    /** The heaplet's object configuration object. */
    protected JsonValue config;

    /** Where objects should be put and where object dependencies should be retrieved. */
    protected Heap heap;

    /** Allocates temporary buffers for caching streamed content during processing. */
    protected TemporaryStorage storage;

    /** The object created by the heaplet's {@link #create()} method. */
    protected Object object;
    private EndpointRegistry.Registration registration;
    private EndpointRegistry registry;

    @Override
    public Object create(Name name, JsonValue config, Heap heap) throws HeapException {
        this.name = name.getLeaf();
        this.qualified = name;
        this.config = config.required().expect(Map.class);
        this.heap = heap;
        if (!SPECIAL_OBJECTS.contains(this.name)) {
            this.storage = config.get("temporaryStorage")
                                 .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                                 .as(requiredHeapObject(heap, TemporaryStorage.class));
        }
        this.object = create();
        if (this.object instanceof GenericHeapObject) {
            // instrument object if possible
            GenericHeapObject ghObject = (GenericHeapObject) this.object;
            ghObject.storage = this.storage;
        }
        start();
        return object;
    }

    /**
     * Returns this object's {@link EndpointRegistry}, creating it lazily when requested for the first time.
     *
     * @return this object's {@link EndpointRegistry} ({@literal /objects/[name]})
     * @throws HeapException
     *         should never be thrown
     */
    protected EndpointRegistry endpointRegistry() throws HeapException {
        if (registry == null) {
            // Get parent registry (.../objects)
            EndpointRegistry parent = heap.get(ENDPOINT_REGISTRY_HEAP_KEY, EndpointRegistry.class);
            Router router = new Router();
            router.addRoute(requestUriMatcher(EQUALS, ""), Handlers.NO_CONTENT);
            String objectName = qualified.getLeaf();
            String slug = slug(objectName);
            if (!slug.equals(objectName)) {
                logger.warn("The heap object name ('{}') has been transformed to a URL-friendly name ('{}') "
                                    + "that is exposed in endpoint URLs. To prevent this message, "
                                    + "consider renaming your heap object with the transformed name, "
                                    + "or provide your own appropriate value.",
                            objectName,
                            slug);
            }
            registration = parent.register(slug, router);
            registry = new EndpointRegistry(router, registration.getPath());
        }
        return registry;
    }

    @Override
    public void destroy() {
        if (registration != null) {
            registration.unregister();
        }
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

    /**
     * Returns a function that will evaluate the expression hold by a
     * {@link JsonValue} using the properties defined in the heap of this
     * Heaplet.
     *
     * @return a function that will evaluate the expression hold by a
     *         {@link JsonValue} using the properties defined in the heap of
     *         this Heaplet.
     */
    protected Function<JsonValue, JsonValue, JsonValueException> evaluatedWithHeapProperties() {
        return evaluated(heap.getProperties());
    }
}
