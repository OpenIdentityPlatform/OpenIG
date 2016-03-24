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

package org.forgerock.openig.thread;

import static org.forgerock.util.Reject.checkNotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Store MDC when tasks are submitted, and re-inject it when tasks are executed.
 */
class MdcScheduledExecutorServiceDelegate extends MdcExecutorServiceDelegate implements ScheduledExecutorService {

    private final ScheduledExecutorService delegate;

    MdcScheduledExecutorServiceDelegate(final ScheduledExecutorService delegate) {
        super(checkNotNull(delegate, "ScheduledExecutorService to delegate cannot be null"));
        this.delegate = delegate;
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command,
                                       final long delay,
                                       final TimeUnit unit) {
        return delegate.schedule(mdcAwareRunnable(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable,
                                           final long delay,
                                           final TimeUnit unit) {
        return delegate.schedule(mdcAwareCallable(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command,
                                                  final long initialDelay,
                                                  final long period,
                                                  final TimeUnit unit) {
        return delegate.scheduleAtFixedRate(mdcAwareRunnable(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
                                                     final long initialDelay,
                                                     final long delay,
                                                     final TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(mdcAwareRunnable(command), initialDelay, delay, unit);
    }
}
