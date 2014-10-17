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
import java.util.Collections;
import java.util.Map;

import org.forgerock.http.spi.ClientImpl;
import org.forgerock.http.spi.TransportProvider;
import org.forgerock.http.util.CaseInsensitiveMap;
import org.forgerock.http.util.Loader;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

/**
 * An HTTP client for sending requests to remote servers.
 */
public final class Client implements Closeable {

    /** Mapping of supported codings to associated providers. */
    private static final Map<String, TransportProvider> PROVIDERS = Collections
            .unmodifiableMap(new CaseInsensitiveMap<TransportProvider>(Loader.loadMap(String.class,
                    TransportProvider.class)));

    /** The client implementation. */
    private final ClientImpl impl;

    /**
     * Creates a new HTTP client using default client options. The returned
     * client must be closed when it is no longer needed by the application.
     *
     * @throws HttpApplicationException
     *             If no transport provider could be found.
     */
    public Client() throws HttpApplicationException {
        this(new ClientOptions());
    }

    /**
     * Creates a new HTTP client using the provided client options. The returned
     * client must be closed when it is no longer needed by the application.
     *
     * @param options
     *            The options which will be used to configure the client.
     * @throws HttpApplicationException
     *             If no transport provider could be found, or if the client
     *             could not be configured using the provided set of options.
     * @throws NullPointerException
     *             If {@code options} was {@code null}.
     */
    public Client(final ClientOptions options) throws HttpApplicationException {
        Reject.ifNull(options);
        this.impl = getTransportProvider(options.getTransportProvider()).newClientImpl(options);
    }

    /**
     * Completes all pending requests and release resources associated with
     * underlying implementation.
     */
    @Override
    public void close() throws IOException {
        impl.close();
    }

    /**
     * Sends an HTTP request to a remote server and returns the response.
     *
     * @param request
     *            The HTTP request to send.
     * @return The HTTP response if the response has a 2xx status code.
     * @throws ResponseException
     *             The HTTP error response if the response did not have a 2xx
     *             status code.
     */
    public Response send(final Request request) throws ResponseException {
        try {
            return sendAsync(request).getOrThrow();
        } catch (final InterruptedException e) {
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
     * @return A promise representing the pending HTTP response. The promise
     *         will yield a {@code ResponseException} when a non-2xx HTTP status
     *         code is returned.
     */
    public Promise<Response, ResponseException> sendAsync(final Request request) {
        return impl.sendAsync(request);
    }

    private TransportProvider getTransportProvider(final String name)
            throws HttpApplicationException {
        if (PROVIDERS.isEmpty()) {
            throw new HttpApplicationException("No transport providers found");
        }
        if (name == null) {
            return PROVIDERS.values().iterator().next();
        }
        if (PROVIDERS.containsKey(name)) {
            return PROVIDERS.get(name);
        }
        throw new HttpApplicationException("The transport provider '" + name + "' was not found");
    }
}
