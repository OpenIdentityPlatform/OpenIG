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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import java.io.IOException;

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.log.LogTimer;

/**
 * Creates a static response containing a simple HTML welcome page.
 */
public class WelcomeHandler extends GenericHandler {

    /**
     * Creates a new welcome page handler.
     */
    public WelcomeHandler() {
        // Nothing to do.
    }

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        try {
            Response response = new Response();
            response.setStatus(200);
            response.setReason("OK");
            response.getHeaders().add("Content-Type", "text/html");
            response.setEntity(new BranchingStreamWrapper(getClass().getResourceAsStream(
                    "welcome.html"), storage));
            exchange.response = response;
        } finally {
            timer.stop();
        }
    }

    /**
     * Creates and initializes a static response handler in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            return new WelcomeHandler();
        }
    }
}
