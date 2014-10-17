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
package org.forgerock.http.spi;

import java.io.Closeable;
import java.io.IOException;

import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.util.promise.Promise;

/**
 * Interface for all classes that actually implement {@code Client}.
 * <p>
 * An implementation class is provided by a {@code TransportProvider}.
 * <p>
 * The implementation can be automatically loaded using the
 * {@code java.util.ServiceLoader} facility if its provider extending
 * {@code TransportProvider} is declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.http.spi.TransportProvider}.
 */
public interface ClientImpl extends Closeable {

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
    public Promise<Response, ResponseException> sendAsync(Request request);

    /**
     * Completes all pending requests and release resources associated with
     * underlying implementation.
     */
    @Override
    public void close() throws IOException;
}
