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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;


@SuppressWarnings("javadoc")
public class RuntimeExceptionFilterTest {

    @Test
    public void shouldReturnTheSameResponse() throws Exception {
        final Response response = new Response(Status.OK);

        Handler next = new ResponseHandler(response);

        RuntimeExceptionFilter filter = new RuntimeExceptionFilter();
        Promise<Response, NeverThrowsException> promise = filter.filter(new RootContext(), new Request(), next);

        assertThat(promise.get()).isSameAs(response);
    }

    @Test
    public void shouldReturnErrorResponseWhenResponseCompletedWithRuntimeException() throws Exception {
        final RuntimeException exception = new RuntimeException("Boom");
        Handler next = new Handler() {
            @Override
            public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
                return runtimeExceptionPromise(exception);
            }
        };

        RuntimeExceptionFilter filter = new RuntimeExceptionFilter();
        Promise<Response, NeverThrowsException> promise = filter.filter(new RootContext(), new Request(), next);
        final Response response = promise.get();

        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getCause()).isSameAs(exception);
    }

    @Test
    public void shouldReturnErrorResponseWhenHandlerThrowsRuntimeException() throws Exception {
        final RuntimeException exception = new RuntimeException("Boom");
        Handler next = new Handler() {
            @Override
            public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
                // Simulate a RuntimeException that could happen before returning a Promise.
                throw exception;
            }
        };

        RuntimeExceptionFilter filter = new RuntimeExceptionFilter();
        Promise<Response, NeverThrowsException> promise = filter.filter(new RootContext(), new Request(), next);
        final Response response = promise.get();

        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getCause()).isSameAs(exception);
    }

    private Promise<Response, NeverThrowsException> runtimeExceptionPromise(final RuntimeException exception) {
        // Cannot create a completed RuntimeExceptionPromise in another way
        return Response.newResponsePromise(null)
                .then(new Function<Response, Response, NeverThrowsException>() {
                    @Override
                    public Response apply(Response value) throws NeverThrowsException {
                        throw exception;
                    }
                });
    }

}
