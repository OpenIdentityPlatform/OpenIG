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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.http.Handler;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.io.IO;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Creates a static response containing a simple HTML welcome page.
 */
public class WelcomeHandler implements Handler {

    private final Factory<Buffer> storage;

    @VisibleForTesting
    WelcomeHandler() {
        this(IO.newTemporaryStorage());
    }

    /**
     * Creates a new welcome page handler.
     * @param storage the temporary storage to use to stream the resource
     */
    public WelcomeHandler(Factory<Buffer> storage) {
        this.storage = storage;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Response response = new Response(Status.OK);
        response.getHeaders().add("Content-Type", "text/html");
        response.setEntity(IO.newBranchingInputStream(getClass().getResourceAsStream("welcome.html"), storage));
        return Promises.newResultPromise(response);
    }

    /**
     * Creates and initializes a static response handler in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @SuppressWarnings("unchecked")
        @Override
        public Object create() throws HeapException {
            return new WelcomeHandler(config.get("temporaryStorage")
                                            .defaultTo(TEMPORARY_STORAGE_HEAP_KEY)
                                            .as(requiredHeapObject(heap, Factory.class)));
        }
    }
}
