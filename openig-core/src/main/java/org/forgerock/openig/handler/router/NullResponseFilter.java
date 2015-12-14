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

package org.forgerock.openig.handler.router;

import static java.lang.String.format;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * This class is used to filter null responses. If the response is {@code null}
 * then an error is logged, containing the requested URI and
 * the {@link Context}'s id.
 */
class NullResponseFilter implements Filter {

    private final Logger logger;

    NullResponseFilter(Logger logger) {
        this.logger = checkNotNull(logger);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        return next.handle(context, request)
                .thenOnResult(new ResultHandler<Response>() {
                    @Override
                    public void handleResult(Response response) {
                        if (response == null) {
                            logger.debug(format("No response available for the request '%s' (id: %s)",
                                                request.getUri().toASCIIString(),
                                                context.getId()));
                        }
                    }
                });
    }
}
