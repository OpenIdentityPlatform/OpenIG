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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.capture;

import static org.forgerock.openig.decoration.capture.CapturePoint.*;

import java.io.IOException;
import java.util.Set;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;

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
     *         Specifies the points where message should be captured
     */
    public CaptureHandler(final Handler delegate,
                          final MessageCapture capture,
                          final Set<CapturePoint> points) {
        this.delegate = delegate;
        this.capture = capture;
        this.points = points;
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {

        try {
            if (points.contains(REQUEST)) {
                capture.capture(exchange, REQUEST);
            }
            delegate.handle(exchange);
        } finally {
            if (points.contains(RESPONSE)) {
                capture.capture(exchange, RESPONSE);
            }
        }
    }
}
