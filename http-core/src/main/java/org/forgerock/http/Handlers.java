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

package org.forgerock.http;

import java.util.Arrays;
import java.util.Collection;

import org.forgerock.util.promise.PromiseImpl;

/**
 * Handler utility methods.
 */
public class Handlers {

    public static AsyncHandler asAsyncHandler(final Handler2 handler) {
        return new AsyncHandler() {

            @Override
            public void handle(final Context context, final Request request,
                    final ResponseHandler callback) throws ResponseException {
                handleResponse(callback, handler.handle(context, request));
            }
        };
    }

    public static Handler2 asHandler(final AsyncHandler handler) {
        return new Handler2() {

            @Override
            public Response handle(final Context context, final Request request)
                    throws ResponseException {
                final PromiseImpl<Response, ResponseException> promise = PromiseImpl.create();
                // FIXME: it's annoying to have to create this extra object.
                handler.handle(context, request, new ResponseHandler() {

                    @Override
                    public void handleError(final ResponseException error) {
                        promise.handleError(error);
                    }

                    @Override
                    public void handleResult(final Response result) {
                        promise.handleResult(result);
                    }
                });
                try {
                    return promise.getOrThrow();
                } catch (final InterruptedException e) {
                    // FIXME: is a 408 time out the best status code?
                    throw new ResponseException(408);
                }
            }
        };
    }

    public static AsyncHandler chain(final AsyncHandler handler, final AsyncFilter... filters) {
        return chain(handler, Arrays.asList(filters));
    }

    public static AsyncHandler chain(final AsyncHandler handler,
            final Collection<AsyncFilter> filters) {
        // TODO: return a filter chain.
        return null;
    }

    static void handleResponse(final ResponseHandler callback, final Response response) {
        if (response.isError()) {
            callback.handleError(new ResponseException(response));
        } else {
            callback.handleResult(response);
        }
    }

    private Handlers() {
        // Prevent instantiation.
    }
}
