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
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

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

    public static Filter asFilter(org.forgerock.http.Filter filter) {
        return new ChfFilterDelegate(filter);
    }

    public static org.forgerock.http.Handler asChfHandler(final Handler handler) {
        return new org.forgerock.http.Handler() {
            @Override
            public Promise<Response, ResponseException> handle(final Context context, final Request request) {
                Exchange exchange = context.asContext(Exchange.class);
                try {
                    exchange.request = request;
                    handler.handle(exchange);
                    return Promises.newSuccessfulPromise(exchange.response);
                } catch (Exception e) {
                    return Promises.newFailedPromise(new ResponseException(e.getMessage(), e));
                }
            }
        };
    }

    public static org.forgerock.http.Filter asChfFilter(final Filter filter) {
        return new org.forgerock.http.Filter() {
            @Override
            public Promise<Response, ResponseException> filter(final Context context,
                                                               final Request request,
                                                               final org.forgerock.http.Handler next) {
                Exchange exchange = context.asContext(Exchange.class);
                exchange.request = request;
                try {
                    filter.filter(exchange, asHandler(next));
                    return Promises.newSuccessfulPromise(exchange.response);
                } catch (Exception e) {
                    return Promises.newFailedPromise(new ResponseException(e.getMessage(), e));
                }
            }
        };
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

    private static class ChfFilterDelegate implements Filter {
        private final org.forgerock.http.Filter filter;

        public ChfFilterDelegate(final org.forgerock.http.Filter filter) {
            this.filter = filter;
        }

        @Override
        public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
            try {
                exchange.response = filter.filter(exchange,
                                                  exchange.request,
                                                  asChfHandler(next)).getOrThrow();
            } catch (InterruptedException e) {
                throw new HandlerException(e);
            } catch (ResponseException re) {
                throw new HandlerException(re);
            }
        }
    }

}
