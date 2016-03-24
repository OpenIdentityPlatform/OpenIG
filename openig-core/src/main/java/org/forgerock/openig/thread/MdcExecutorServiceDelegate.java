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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.util.Reject;
import org.forgerock.util.annotations.VisibleForTesting;
import org.slf4j.MDC;

/**
 * Store MDC when tasks are submitted, and re-inject it when tasks are executed.
 */
class MdcExecutorServiceDelegate implements ExecutorService {

    private final ExecutorService delegate;

    MdcExecutorServiceDelegate(final ExecutorService delegate) {
        Reject.ifNull(delegate, "ExecutorService to delegate cannot be null");
        this.delegate = delegate;
    }

    protected static <V> Callable<V> mdcAwareCallable(final Callable<V> delegate) {
        Callable<V> task = delegate;
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc != null) {
            task = new MdcCallable<>(delegate, mdc);
        }
        return task;
    }

    protected static Runnable mdcAwareRunnable(final Runnable delegate) {
        Runnable runnable = delegate;
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc != null) {
            runnable = new MdcRunnable(delegate, mdc);
        }
        return runnable;
    }

    private static <T> Collection<? extends Callable<T>> mdcAwareCollection(final Collection<? extends Callable<T>> c) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc == null) {
            return c;
        }
        Collection<Callable<T>> collection = new ArrayList<>(c.size());
        for (Callable<T> task : c) {
            collection.add(new MdcCallable<>(task, mdc));
        }
        return collection;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return delegate.submit(mdcAwareCallable(task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return delegate.submit(mdcAwareRunnable(task), result);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return delegate.submit(mdcAwareRunnable(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(mdcAwareCollection(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
                                         final long timeout, final TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(mdcAwareCollection(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(mdcAwareCollection(tasks));
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
                           final long timeout,
                           final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(mdcAwareCollection(tasks), timeout, unit);
    }

    @Override
    public void execute(final Runnable command) {
        delegate.execute(mdcAwareRunnable(command));
    }

    @VisibleForTesting
    static class MdcRunnable implements Runnable {
        private final Runnable delegate;
        private final Map<String, String> mdc;

        MdcRunnable(final Runnable delegate, final Map<String, String> mdc) {
            this.delegate = delegate;
            this.mdc = mdc;
        }

        @Override
        public void run() {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        }
    }

    @VisibleForTesting
    static class MdcCallable<V> implements Callable<V> {
        private final Callable<V> delegate;
        private final Map<String, String> mdc;

        MdcCallable(final Callable<V> delegate, final Map<String, String> mdc) {
            this.delegate = delegate;
            this.mdc = mdc;
        }

        @Override
        public V call() throws Exception {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                return delegate.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        }
    }
}
