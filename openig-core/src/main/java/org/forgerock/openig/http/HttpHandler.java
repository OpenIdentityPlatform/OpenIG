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
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.http;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

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
    private final URI baseURI;

    /**
     * Constructs a new {@code ServletHandler} instance.
     *
     * @param handler The configured {@code Handler}.
     * @param baseURI The base URI for all HTTP request. Can be {@code null}.
     */
    HttpHandler(org.forgerock.openig.handler.Handler handler, URI baseURI) {
        this.handler = handler;
        this.baseURI = baseURI;
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
        if (baseURI != null) {
            request.getUri().rebase(baseURI);
        }
        Exchange exchange = new RequestAdapter(context, request);
        try {
            handler.handle(exchange);
            return Promises.newSuccessfulPromise(exchange.response);
        } catch (HandlerException e) {
            LOG.error(e.getMessage(), e);
            return Promises.newFailedPromise(new ResponseException(exchange.response, e.getMessage(), e));
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return Promises.newFailedPromise(new ResponseException(exchange.response, e.getMessage(), e));
        }
    }
}
