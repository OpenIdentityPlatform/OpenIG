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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openig.heap;

import static org.forgerock.openig.heap.Keys.BASEURI_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENDPOINT_REGISTRY_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;

import org.forgerock.http.routing.Router;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.util.time.TimeService;

/**
 * Utility class for tests usage.
 */
public final class HeapUtilsTest {
    /** Static methods only. */
    private HeapUtilsTest() {
    }

    public static HeapImpl buildDefaultHeap() throws Exception {
        HeapImpl heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(BASEURI_HEAP_KEY, new BaseUriDecorator(BASEURI_HEAP_KEY));
        heap.put(ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(new Router(), "/"));
        heap.put(TIME_SERVICE_HEAP_KEY, TimeService.SYSTEM);
        return heap;
    }
}
