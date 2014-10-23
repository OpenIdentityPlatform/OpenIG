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

package org.forgerock.openig.decoration.timer;

import java.io.IOException;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;

/**
 * Log a {@literal started} message when an {@link Exchange} is flowing into this Handler and an {@literal elapsed}
 * message when the {@link Exchange} is flowing out, delegating to a given encapsulated {@link Handler} instance.
 */
class TimerHandler implements Handler {
    private final Handler delegate;
    private final Logger logger;

    public TimerHandler(final Handler delegate, final Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        try {
            delegate.handle(exchange);
        } finally {
            timer.stop();
        }
    }
}
