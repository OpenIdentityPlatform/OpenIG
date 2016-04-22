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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.http;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;

/**
 * This a value class to hold a {@link Context} and a {@link Request} during the processing of a request.
 * It can be used as a parameter in a {@link org.forgerock.util.Function}.
 * Example:
 *
 * <pre>
 * {@code
 * public class EnforcementFilter implements Filter {
 *
 *     private final AsyncFunction<ContextAndRequest, Boolean, Exception> condition;
 *
 *     public EnforcementFilter(AsyncFunction<ContextAndRequest, Boolean, Exception> condition) {
 *         this.condition = condition;
 *     }
 *
 *     {@literal @}Override
 *     public Promise<Response, NeverThrowsException> filter(final Context context, final Request request,
 *             final Handler next) {
 *         return condition.apply(new ContextAndRequest(context, request))
 *                         .thenAsync(new AsyncFunction<Boolean, Response, NeverThrowsException>() {
 *                                        {@literal @}Override
 *                                        public Promise<? extends Response, ? extends NeverThrowsException> apply(
 *                                                Boolean condition) throws NeverThrowsException {
 *                                            if (condition) {
 *                                                return next.handle(context, request);
 *                                            }
 *                                            return newResponsePromise(new Response(Status.FORBIDDEN));
 *                                        }
 *                                    },
 *                                    new AsyncFunction<Exception, Response, NeverThrowsException>() {
 *                                        {@literal @}Override
 *                                        public Promise<? extends Response, ? extends NeverThrowsException> apply(
 *                                                Exception cause) throws NeverThrowsException {
 *                                            Response response = new Response(Status.INTERNAL_SERVER_ERROR);
 *                                            response.setCause(cause);
 *                                            return newResponsePromise(response);
 *                                        }
 *                                    });
 *     }
 * }
 * }
 * </pre>
 *
 * @see org.forgerock.http.filter.throttling.ThrottlingFilter
 */
public class ContextAndRequest {

    private final Context context;
    private final Request request;

    /**
     * Constructs a new ContextAndRequest.
     * @param context the context
     * @param request the request
     */

    public ContextAndRequest(Context context, Request request) {
        this.context = context;
        this.request = request;
    }

    /**
     * Returns the context.
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    /**
     * Returns the request.
     * @return the request
     */
    public Request getRequest() {
        return request;
    }
}
