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


import static org.forgerock.openig.http.Responses.newInternalServerError;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;

/**
 * This filter aims to guarantee the caller that it will always get a Response to process, even if the {@literal next}
 * returns a promise completed with a {@link RuntimeException}, or even if a {@link RuntimeException} is thrown.
 */
public class RuntimeExceptionFilter implements Filter {

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        // Wraps the result's promise into another promise so we can ensure that in every case we return a Response.
        final PromiseImpl<Response, NeverThrowsException> promise = PromiseImpl.create();

        try {
            next.handle(context, request)
                    .thenOnResult(new ResultHandler<Response>() {
                        @Override
                        public void handleResult(Response result) {
                            promise.handleResult(result);
                        }
                    })
                    .thenOnRuntimeException(onRuntimeException(promise));
            // Note : it's not possible to instantiate a NeverThrowsException so there's no need to add an
            // ExceptionHandler<NeverThrowsException>
        } catch (RuntimeException exception) {
            // next.handle can throw such exceptions
            onRuntimeException(promise).handleRuntimeException(exception);
        }
        return promise;
    }

    private RuntimeExceptionHandler onRuntimeException(final PromiseImpl<Response, NeverThrowsException> promise) {
        return new RuntimeExceptionHandler() {
            @Override
            public void handleRuntimeException(RuntimeException exception) {
                promise.handleResult(errorResponse(exception));
            }
        };
    }

    private static Response errorResponse(Exception exception) {
        return newInternalServerError().setCause(exception);
    }

}
