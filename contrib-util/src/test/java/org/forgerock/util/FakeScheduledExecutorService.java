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

package org.forgerock.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class FakeScheduledExecutorService implements ScheduledExecutorService, FakeTimeService.TimeServiceListener {

    private List<ScheduledTask<?>> tasks = new ArrayList<>();
    private final FakeTimeService time;
    private long currentTimestamp;

    public FakeScheduledExecutorService(FakeTimeService time) {
        this.time = time;
        time.registerTimeServiceListener(this);
        this.currentTimestamp = time.now();
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, long delay, TimeUnit unit) {
        //@Checkstyle:off
        ScheduledTask<Object> task = new ScheduledTask<>(currentTimestamp + unit.toMillis(delay),
                                                         new Callable<Object>() {
                                                             @Override
                                                             public Object call() throws Exception {
                                                                 command.run();
                                                                 return null;
                                                             }
                                                         });
        //@Checkstyle:on

        tasks.add(task);
        return task;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledTask<V> task = new ScheduledTask<>(currentTimestamp + unit.toMillis(delay), callable);

        tasks.add(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw notYetImplemented();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw notYetImplemented();
    }

    @Override
    public void shutdown() {
        throw notYetImplemented();
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw notYetImplemented();
    }

    @Override
    public boolean isShutdown() {
        throw notYetImplemented();
    }

    @Override
    public boolean isTerminated() {
        throw notYetImplemented();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw notYetImplemented();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        ScheduledTask<T> scheduledTask = new ScheduledTask<>(currentTimestamp, task);
        scheduledTask.execute();
        return scheduledTask;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw notYetImplemented();
    }

    @Override
    public Future<?> submit(final Runnable task) {
        ScheduledTask<Object> scheduledTask = new ScheduledTask<>(currentTimestamp, new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                task.run();
                return null;
            }
        });
        scheduledTask.execute();
        return scheduledTask;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw notYetImplemented();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        throw notYetImplemented();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw notYetImplemented();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throw notYetImplemented();
    }

    @Override
    public void execute(Runnable command) {
        submit(command);
    }

    @Override
    public void notifyCurrentTime(long current) {
        this.currentTimestamp = current;
        // Iterate over all the tasks and execute the ones that should have been raised
        for (ScheduledTask<?> task : tasks) {
            if (task.getExecutionTimestamp() <= currentTimestamp) {
                task.execute();
            }
        }
    }

    private class ScheduledTask<V> implements ScheduledFuture<V> {

        private long executionTimestamp;
        private final Callable<V> command;
        private boolean cancelled = false;
        private boolean done = false;
        private V result;

        public ScheduledTask(long executionTimestamp, Callable<V> command) {
            this.executionTimestamp = executionTimestamp;
            this.command = command;
        }

        public long getExecutionTimestamp() {
            return executionTimestamp;
        }

        public void execute() {
            try {
                result = command.call();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done = true;
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(currentTimestamp - time.now(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            throw notYetImplemented();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return tasks.remove(this);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return result;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return result;
        }
    }

    private RuntimeException notYetImplemented() {
        return new RuntimeException("Not yet implemented");
    }
}
