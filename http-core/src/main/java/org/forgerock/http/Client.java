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

package org.forgerock.http;

import java.io.Closeable;
import java.io.IOException;

import org.forgerock.util.promise.Promise;

/**
 * An HTTP client for sending requests to remote servers.
 */
public final class Client implements Closeable {
    /**
     * Creates a new HTTP client using default client options. The returned
     * client must be closed when it is no longer needed by the application.
     */
    public Client() {
        this(null);
    }

    /**
     * Creates a new HTTP client using the provided client options. The returned
     * client must be closed when it is no longer needed by the application.
     *
     * @param options
     */
    public Client(ClientOptions options) {
        // TODO: use service loader to load implementation.
    }

    /**
     * Sends an HTTP request to a remote server and returns the response.
     *
     * @param request
     *            The HTTP request to send.
     * @return The HTTP response if the response has a 2xx status code.
     * @throws ResponseException
     *             If the HTTP error response if the response did not have a 2xx
     *             status code.
     */
    public Response send(Request request) throws ResponseException {
        try {
            return sendAsync(request).getOrThrow();
        } catch (InterruptedException e) {
            // FIXME: is a 408 time out the best status code?
            throw new ResponseException(408);
        }
    }

    /**
     * Sends an HTTP request to a remote server and returns a {@code Promise}
     * representing the asynchronous response.
     *
     * @param request
     *            The HTTP request to send.
     * @return The HTTP response if the response has a 2xx status code.
     */
    public Promise<Response, ResponseException> sendAsync(Request request) {
        // TODO: delegate to underlying implementation.
        return null;
    }

    /**
     * Completes all pending requests and release resources associated with
     * underlying implementation.
     */
    @Override
    public void close() throws IOException {
        // TODO: delegate to underlying implementation.
    }
}
