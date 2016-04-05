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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This filter is in charge to create a defensive copy of the {@link Request} on
 * which the chain of execution will be based on. At the end of the chain of
 * execution, the copy request will be closed.
 * <p>
 * This can be helpful when it is needed to reuse the original request(as in the
 * {@link HttpBasicAuthFilter} or in the {@link PasswordReplayFilterHeaplet}).
 */
final class RequestCopyFilter implements Filter {

    private static final RequestCopyFilter INSTANCE = new RequestCopyFilter();

    private RequestCopyFilter() { }

    public static RequestCopyFilter requestCopyFilter() {
        return INSTANCE;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final Request requestCopy;
        try {
            requestCopy = new Request(request);
            return next.handle(context, requestCopy).thenAlways(new Runnable() {
                @Override
                public void run() {
                    closeSilently(requestCopy);
                }
            });
        } catch (IOException ioe) {
            return newResponsePromise(newInternalServerError(ioe));
        }
    }
}
