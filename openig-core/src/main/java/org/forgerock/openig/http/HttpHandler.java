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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.forgerock.openig.http.Adapters.*;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root {@link Handler} for the Servlet Gateway.
 *
 * @since 3.1.0
 */
final class HttpHandler implements Handler {

    /**
     * {@link Logger} instance for the openig-war module.
     */
    static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class);

    private final org.forgerock.openig.handler.Handler handler;

    /**
     * Constructs a new {@code ServletHandler} instance.
     *
     * @param handler The configured {@code Handler}.
     */
    HttpHandler(org.forgerock.openig.handler.Handler handler) {
        this.handler = handler;
    }

    /**
     * Rebases the request with the configured {@literal baseURI}, if not {@code null} before calling the configured
     * {@code Handler}.
     *
     * @param context {@inheritDoc}
     * @param request {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public Promise<Response, ResponseException> handle(Context context, Request request) {
        // Builds the only Exchange and use it
        Exchange exchange = asExchange(context, request);
        try {
            handler.handle(exchange);
            // Propagate exchange properties/attributes to the context
            return Promises.newSuccessfulPromise(exchange.response);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Promises.newFailedPromise(new ResponseException("Can't handle the request", e));
        }
    }
}
