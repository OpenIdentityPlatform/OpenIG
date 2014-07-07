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
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import java.io.IOException;

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.log.LogTimer;

/**
 * Submits exchange requests to remote servers. In this implementation, requests
 * are dispatched through the <a href="http://hc.apache.org/">Apache
 * HttpComponents</a> client.
 * <p>
 * <strong>Note:</strong> This handler does not verify hostnames for outgoing
 * SSL connections. This is because the gateway will usually access the SSL
 * endpoint using a raw IP address rather than a fully-qualified hostname.
 */
public class ClientHandler extends GenericHandler {

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
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        try {
            client.execute(exchange);
        } finally {
            timer.stop();
        }
    }

    /** Creates and initializes a client handler in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            // optional, default to DEFAULT_CONNECTIONS number of connections
            Integer connections = config.get("connections").defaultTo(0).asInteger();
            // determines if connections should be reused, disables keep-alive
            Boolean disableReuseConnection = config.get("disableReuseConnection")
                    .defaultTo(false).asBoolean();
            // determines if requests should be retried on failure
            Boolean disableRetries = config.get("disableRetries")
                    .defaultTo(false).asBoolean();

            HttpClient client = new HttpClient(storage, connections);
            if (disableRetries) {
                client.disableRetries(logger);
            }
            if (disableReuseConnection) {
                client.disableConnectionReuse();
            }
            return new ClientHandler(client);
        }
    }
}
