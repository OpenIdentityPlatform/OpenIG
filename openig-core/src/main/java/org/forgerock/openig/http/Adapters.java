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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import java.io.IOException;

import org.forgerock.http.context.ClientInfoContext;
import org.forgerock.http.Context;
import org.forgerock.http.context.HttpContext;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;

/**
 * Adapters for converting between HTTP framework and legacy OpenIG APIs.
 *
 * FIXME: Temporary until the IG migrates to using the http framework {@link org.forgerock.http.Handler}.
 */
public final class Adapters {

    private Adapters() {
        // Prevent instantiation.
    }

    /**
     * Builds the exchange from the current context and request.
     *
     * @param context
     *            The context associated with a request currently being
     *            processed.
     * @param request
     *            The current request.
     * @return An HTTP exchange of request which can be used in legacy OpenIG.
     */
    public static Exchange asExchange(Context context, Request request) {
        HttpContext requestContext = context.asContext(HttpContext.class);
        final Exchange exchange = new Exchange(context, request.getUri().asURI());
        exchange.setClientInfo(context.asContext(ClientInfoContext.class));
        exchange.setPrincipal(requestContext.getPrincipal());
        exchange.setSession(requestContext.getSession());
        exchange.setRequest(request);
        // TODO We will need to find a more robust solution when Exchange will be removed
        exchange.putAll(requestContext.getAttributes());
        requestContext.getAttributes().clear();
        return exchange;
    }

    /**
     * Converts a {@link org.forgerock.http.Handler} to a
     * {@link org.forgerock.openig.handler.Handler} used in scripts.
     *
     * @param handler
     *            The handler to converts to.
     * @return A {@link org.forgerock.openig.handler.Handler} used in scripts.
     */
    public static Handler asHandler(org.forgerock.http.Handler handler) {
        return new ChfHandlerDelegate(handler);
    }

    private static class ChfHandlerDelegate implements Handler {
        private final org.forgerock.http.Handler handler;

        public ChfHandlerDelegate(final org.forgerock.http.Handler handler) {
            this.handler = handler;
        }

        @Override
        public void handle(final Exchange exchange) throws HandlerException, IOException {
            try {
                exchange.setResponse(handler.handle(exchange, exchange.getRequest()).getOrThrow());
            } catch (InterruptedException e) {
                throw new HandlerException(e);
            }
        }
    }

}
