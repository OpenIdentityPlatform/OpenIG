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

package org.forgerock.openig.handler.router;

import static com.forgerock.opendj.util.StaticUtils.*;
import static java.lang.String.*;
import static org.forgerock.openig.util.Json.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;

/**
 * Builder for new {@link Route}s.
 *
 * @since 2.2
 */
class RouteBuilder {

    /**
     * Heap to be used as parent for routes built from this builder.
     */
    private final Heap heap;

    /**
     * Builds a new builder.
     * @param heap parent heap for produced routes
     */
    public RouteBuilder(final Heap heap) {
        this.heap = heap;
    }

    /**
     * Builds a new route from the given resource file.
     *
     * @param resource route definition
     * @return a new configured Route
     * @throws HeapException if the new Route cannot be build
     */
    public Route build(final File resource) throws HeapException {
        JsonValue config = readJson(resource);
        HeapImpl newHeap = createHeap(config);
        return new Route(newHeap, config, resource.getName());
    }

    /**
     * Reads the raw Json content from the route's definition file.
     *
     * @param resource
     *            The route definition file.
     * @return Json structure
     * @throws HeapException
     *             if there are IO or parsing errors
     */
    private JsonValue readJson(final File resource) throws HeapException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(resource);
            return new JsonValue(readJsonLenient(new FileInputStream(resource)));
        } catch (FileNotFoundException e) {
            throw new HeapException(format("File %s does not exists", resource), e);
        } catch (IOException e) {
            throw new HeapException(format("Cannot read/parse content of %s", resource), e);
        } finally {
            closeSilently(fis);
        }
    }

    /**
     * Creates and initialize a new child {@link Heap} from the previously parsed Json content.
     *
     * @param config parsed route definition
     * @return initialized child heap
     * @throws HeapException if there is some semantic errors in the route definition
     */
    private HeapImpl createHeap(final JsonValue config) throws HeapException {
        HeapImpl child = new HeapImpl(heap);
        child.init(config.get("heap").required().expect(Map.class));
        return child;
    }

}
