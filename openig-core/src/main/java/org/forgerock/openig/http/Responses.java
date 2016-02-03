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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.concurrent.CountDownLatch;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Provide out-of-the-box, pre-configured {@link Response} objects.
 */
public final class Responses {

    private static final Function<NeverThrowsException, Object, Exception> NOOP_EXCEPTION_FUNC =
            new Function<NeverThrowsException, Object, Exception>() {
                @Override
                public Object apply(final NeverThrowsException value) throws Exception {
                    return null;
                }
            };

    private static final AsyncFunction<Exception, Response, NeverThrowsException> INTERNAL_SERVER_ERROR_ASYNC_FUNC =
            new AsyncFunction<Exception, Response, NeverThrowsException>() {
                @Override
                public Promise<Response, NeverThrowsException> apply(Exception e) {
                    return newResultPromise(newInternalServerError(e));
                }
            };

    /**
     * Empty private constructor for utility.
     */
    private Responses() { }

    /**
     * Generates an empty {@literal Internal Server Error} response ({@literal 500}).
     *
     * @return an empty {@literal Internal Server Error} response ({@literal 500}).
     */
    public static Response newInternalServerError() {
        return new Response(Status.INTERNAL_SERVER_ERROR);
    }

    /**
     * Generates an {@literal Internal Server Error} response ({@literal 500})
     * containing the cause of the error response.
     *
     * @param exception
     *            wrapped exception
     * @return an empty {@literal Internal Server Error} response {@literal 500}
     *         with the cause set.
     */
    public static Response newInternalServerError(Exception exception) {
        return newInternalServerError().setCause(exception);
    }

    /**
     * Generates an empty {@literal Not Found} response ({@literal 404}).
     *
     * @return an empty {@literal Not Found} response ({@literal 404}).
     */
    public static Response newNotFound() {
        return new Response(Status.NOT_FOUND);
    }

    /**
     * Executes a blocking call with the given {@code handler}, {@code context} and {@code request}, returning
     * the {@link Response} when fully available.
     *
     * <p>This function is here to fix a concurrency issue where a caller thread is blocking a promise and is
     * resumed before all of the ResultHandlers and Function of the blocked promise have been invoked.
     * That may lead to concurrent consumption of {@link org.forgerock.http.io.BranchingInputStream} that is a
     * not thread safe object.
     *
     * @param handler Handler for handling the given request
     * @param context Context to be used for the invocation
     * @param request request to be executed
     * @return a ready to used {@link Response}
     * @throws InterruptedException if either {@link org.forgerock.util.promise.Promise#getOrThrow()} or
     *         {@link CountDownLatch#await()} is interrupted.
     */
    public static Response blockingCall(final Handler handler, final Context context, final Request request)
            throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        Response response = handler.handle(context, request)
                                   // Decrement the latch at the very end of the listener's sequence
                                   .thenOnResult(new ResultHandler<Response>() {
                                       @Override
                                       public void handleResult(Response result) {
                                           latch.countDown();
                                       }
                                   })
                                   // Block the promise, waiting for the response
                                   .getOrThrow();

        // Wait for the latch to be released so we can make sure that all of the Promise's ResultHandlers and Functions
        // have been invoked
        latch.await();

        return response;
    }

    /**
     * Utility method returning an empty function, whose goal is to ease the transformation of a
     * {@link org.forgerock.util.promise.Promise} type. Its main usage will be as the second argument in
     * {@link org.forgerock.util.promise.Promise#then(Function, Function)}. The implementation of this function is just
     * to return null : as its name suggests it, an {@code Exception} of type {@link NeverThrowsException} will never
     * be thrown.
     *
     * @param <V>
     *         The expected type of that function
     * @param <E>
     *         The new {@link Exception} that can be thrown by this function.
     * @return a function that will return {@literal null} and not throw any {@code Exception}.
     */
    public static <V, E extends Exception> Function<NeverThrowsException, V, E> noopExceptionFunction() {
        return (Function<NeverThrowsException, V, E>) NOOP_EXCEPTION_FUNC;
    }

    /**
     * Utility method returning an async function that creates a {@link Response} with status
     * {@link Status#INTERNAL_SERVER_ERROR} and the exception set as the cause.
     *
     * @param <E>
     *         The type of the incoming {@link Exception}
     * @return an async function that creates a {@link Response} with status {@link Status#INTERNAL_SERVER_ERROR}
     * and the exception set as the cause.
     */
    public static <E extends Exception> AsyncFunction<E, Response, NeverThrowsException> internalServerError() {
        return (AsyncFunction<E, Response, NeverThrowsException>) INTERNAL_SERVER_ERROR_ASYNC_FUNC;
    }
}
