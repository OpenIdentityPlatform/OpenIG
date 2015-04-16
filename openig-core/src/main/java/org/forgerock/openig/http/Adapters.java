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

import org.forgerock.http.ClientInfoContext;
import org.forgerock.http.Context;
import org.forgerock.http.HttpContext;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.ResponseException;
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

    public static Exchange asExchange(Context context, Request request) {
        HttpContext requestContext = context.asContext(HttpContext.class);
        final Exchange exchange = new Exchange(request.getUri().asURI());
        exchange.parent = context;
        exchange.clientInfo = context.asContext(ClientInfoContext.class);
        exchange.exchange = exchange;
        exchange.principal = requestContext.getPrincipal();
        exchange.session = requestContext.getSession();
        exchange.request = request;
        // FIXME: exchange field map should write through to context attributes.
        exchange.putAll(requestContext.getAttributes());
        return exchange;
    }

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
                exchange.response = handler.handle(exchange, exchange.request).getOrThrow();
            } catch (InterruptedException e) {
                throw new HandlerException(e);
            } catch (ResponseException re) {
                throw new HandlerException(re);
            }
        }
    }

}
