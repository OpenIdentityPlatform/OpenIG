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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Catches any exceptions thrown during handing of a request. This allows friendlier error
 * pages to be displayed than would otherwise be displayed by the container. Caught exceptions
 * are logged with a log level of {@link org.forgerock.openig.log.LogLevel#WARNING} and the exchange is diverted to
 * the specified exception handler.
 * <p>
 * Note: While the response object will be retained in the exchange object, this class will
 * close any open entity within the response object prior to dispatching the exchange to the
 * exception handler.
 */
public class ExceptionFilter extends GenericHeapObject implements org.forgerock.http.Filter {

    /**
     * Idempotent AsyncFunction for successful Promise.
     */
    private static final AsyncFunction<Response, Response, ResponseException> NOOP_ASYNCFUNCTION = new AsyncFunction<Response, Response, ResponseException>() {
        @Override
        public Promise<Response, ResponseException> apply(final Response value) throws ResponseException {
            return Promises.newSuccessfulPromise(value);
        }
    };

    /** Handler to dispatch to in the event of caught exceptions. */
    private final Handler handler;

    /**
     * Build a new exception filter that will divert the flow to the given handler in case of exception.
     * @param handler exception handler
     */
    public ExceptionFilter(final Handler handler) {
        this.handler = handler;
    }

    @Override
    public Promise<Response, ResponseException> filter(final Context context,
                                                       final Request request,
                                                       final Handler next) {
        return next.handle(context, request)
                .thenAsync(NOOP_ASYNCFUNCTION, new AsyncFunction<ResponseException, Response, ResponseException>() {
                    @Override
                    public Promise<Response, ResponseException> apply(final ResponseException value)
                            throws ResponseException {
                        logger.warning(value);
                        return handler.handle(context, request);
                    }
                });
    }

    /**
     * Creates and initializes an exception filter in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            return new ExceptionFilter(heap.resolve(config.get("handler"), Handler.class));
        }
    }
}
