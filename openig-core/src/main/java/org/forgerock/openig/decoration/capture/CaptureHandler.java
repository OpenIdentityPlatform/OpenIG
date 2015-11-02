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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.capture;

import static org.forgerock.openig.decoration.capture.CapturePoint.REQUEST;
import static org.forgerock.openig.decoration.capture.CapturePoint.RESPONSE;
import static org.forgerock.util.Reject.checkNotNull;

import java.util.Set;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

/**
 * Capture both requests and responses, delegating to a given encapsulated {@link Handler} instance.
 */
class CaptureHandler implements Handler {
    private final Handler delegate;
    private final MessageCapture capture;
    private final Set<CapturePoint> points;

    /**
     * Builds a new DebugHandler that will decorate the given delegate Handler instance.
     * @param delegate
     *         decorated Handler
     * @param capture
     *         specifies where the messages are going to be captured
     * @param points
     *         Specifies the points where message should be captured, not null.
     */
    public CaptureHandler(final Handler delegate,
                          final MessageCapture capture,
                          final Set<CapturePoint> points) {
        this.delegate = delegate;
        this.capture = capture;
        this.points = checkNotNull(points);
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        if (points.contains(REQUEST)) {
            capture.capture(context, request, REQUEST);
        }
        return delegate.handle(context, request)
                .thenOnResult(new ResultHandler<Response>() {
                    @Override
                    public void handleResult(final Response response) {
                        if (points.contains(RESPONSE)) {
                            capture.capture(context, response, RESPONSE);
                        }
                    }
                });
    }
}
