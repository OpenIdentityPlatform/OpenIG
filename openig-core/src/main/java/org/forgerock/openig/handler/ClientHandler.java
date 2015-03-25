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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.forgerock.openig.http.HttpClient.*;

import org.forgerock.http.Context;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Submits exchange requests to remote servers. In this implementation, requests are dispatched through the {@link
 * HttpClient} this handler is configured to use (defaults to system's HttpClient provided under the {@literal
 * HttpClient} name).
 * <p>
 * <pre>
 *   {
 *     "name": "Client",
 *     "type": "ClientHandler",
 *     "config": {
 *       "httpClient": "MyHttpClient"
 *     }
 *   }
 * </pre>
 */
public class ClientHandler extends GenericHeapObject implements org.forgerock.http.Handler {

    /** The HTTP client to transmit requests through. */
    private final HttpClient client;

    /**
     * Creates a new client handler.
     *
     * @param client The HTTP client implementation.
     */
    public ClientHandler(HttpClient client) {
        this.client = client;
    }

    @Override
    public Promise<Response, ResponseException> handle(final Context context, final Request request) {
        // TODO Maybe this could be done asynchronously
        return Promises.newSuccessfulPromise(client.execute(request));
    }

    /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            HttpClient httpClient = heap.resolve(
                    config.get("httpClient").defaultTo(HTTP_CLIENT_HEAP_KEY),
                    HttpClient.class);
            return new ClientHandler(httpClient);
        }
    }
}
