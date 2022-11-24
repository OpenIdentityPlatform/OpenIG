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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static org.forgerock.openig.util.StringUtil.toSIAbbreviation;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;

/**
 * Capture time elapsed in a delegated {@link Handler}.
 */
class TimerHandler implements Handler {

    private final Handler delegate;
    private final Ticker ticker;
    private final Logger logger;
    private final TimeUnit timeUnit;

    TimerHandler(final Handler delegate, final Logger logger, final Ticker ticker, final TimeUnit timeUnit) {
        this.delegate = delegate;
        this.logger = logger;
        this.ticker = ticker;
        this.timeUnit = timeUnit;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        final Stopwatch timeWatch = Stopwatch.createStarted(ticker);
        return delegate.handle(context, request)
                       .thenAlways(new Runnable() {
                           @Override
                           public void run() {
                               timeWatch.stop();
                               if (logger.isDebugEnabled()) {
	                               logger.debug("Elapsed time: {} {}",
	                                           timeWatch.elapsed(timeUnit),
	                                           toSIAbbreviation(timeUnit));
                               }
                           }
                       });
    }
}
