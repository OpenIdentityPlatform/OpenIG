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

import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;

/**
 * Log a {@literal started} message when an {@link Exchange} is flowing into this Filter and both a {@literal
 * elapsed} and (potentially, if not equals to the globally elapsed time) {@literal elapsed-within}
 * messages when the {@link Exchange} is flowing out, delegating to a given encapsulated {@link Filter} instance.
 */
class TimerFilter implements Filter {
    private final Filter delegate;
    private final Logger logger;

    public TimerFilter(final Filter delegate, final Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
        final LogTimer timer = logger.getTimer().start();
        try {
            // Wraps the next handler to mark when the flow exits/re-enter the delegated filter
            // Used to pause/resume the timer
            delegate.filter(exchange, new Handler() {
                @Override
                public void handle(final Exchange exchange) throws HandlerException, IOException {
                    try {
                        timer.pause();
                        next.handle(exchange);
                    } finally {
                        timer.resume();
                    }
                }
            });
        } finally {
            timer.stop();
        }
    }

}
