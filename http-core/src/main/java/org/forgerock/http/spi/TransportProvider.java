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

import org.forgerock.http.ClientOptions;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.util.Indexed;

/**
 * Interface for transport providers, which provide implementations of HTTP
 * {@code Client}s using a specific transport.
 * <p>
 * A transport provider must be declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.http.spi.TransportProvider} in order
 * to allow automatic loading of the implementation classes using the
 * {@code java.util.ServiceLoader} facility.
 */
public interface TransportProvider extends Indexed<String> {
    /**
     * Returns an implementation of {@code Client}.
     *
     * @param options
     *            The client options (never {@code null}).
     * @return An implementation of {@code Client}
     * @throws HttpApplicationException
     *             If the client implementation could not be configured using
     *             the provided set of options.
     */
    ClientImpl newClientImpl(ClientOptions options) throws HttpApplicationException;
}
