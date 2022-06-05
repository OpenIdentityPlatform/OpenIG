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

package org.forgerock.openig.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.Matchers.any;

import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Heaplet;
import org.forgerock.openig.heap.Name;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ScheduledExecutorServiceHeapletTest {

    @Test
    public void shouldDestroyTheUnderlyingExecutorService() throws Exception {

        Heaplet heaplet = new ScheduledExecutorServiceHeaplet();
        ExecutorService service = createExecutorService(heaplet, json(object()));
        assertThat(service).isNotNull();

        heaplet.destroy();
        assertThat(service.isTerminated()).isTrue();
    }

    @Test
    public void shouldWaitForTaskToComplete() throws Exception {

        JsonValue config = json(object(field("gracefulStop", true),
                                       field("gracePeriod", "100 ms")));

        Heaplet heaplet = new ScheduledExecutorServiceHeaplet();
        ExecutorService service = createExecutorService(heaplet, config);

        AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        service.submit(new ManagedSleepingRunnable(20, state, started, completed));

        started.await();

        // task has not completed yet
        assertThat(state.get()).isNotIn(State.DONE, State.KILLED);

        // Attempt to destroy the heaplet
        // Should wait up to 30ms for the task to finish
        heaplet.destroy();

        // task have been completed normally
        assertThat(state.get()).isEqualTo(State.DONE);

        assertThat(service.isTerminated()).isTrue();
    }

    private ExecutorService createExecutorService(final Heaplet heaplet, final JsonValue config) throws Exception {
        return (ScheduledExecutorService) heaplet.create(Name.of("this"),
                                                         config,
                                                         buildDefaultHeap());
    }

    @Test
    public void shouldNotWaitForTaskToComplete() throws Exception {

        JsonValue config = json(object(field("gracefulStop", true)));

        Heaplet heaplet = new ScheduledExecutorServiceHeaplet();

        ExecutorService service = createExecutorService(heaplet, config);
        Field declaredField = service.getClass().getDeclaredField("delegate");
        declaredField.setAccessible(true);

        final ScheduledExecutorService scheduledExecutorService =  (ScheduledExecutorService) declaredField.get(service);

        service = Mockito.spy(service);

        Mockito.doAnswer(i -> {
            Runnable runnable = i.getArgumentAt(0, Runnable.class);
            return scheduledExecutorService.schedule(runnable, 100, TimeUnit.MILLISECONDS);
        }).when(service).submit(any(Runnable.class));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        Future<?> future = service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    e.printStackTrace();
                }
            }
        });

        // Attempt to destroy the heaplet
        // Should exit quickly without killing the task
        heaplet.destroy();
        Thread.sleep(100);
        // task has been completed (using cancel())
        assertThat(future.isCancelled()).isTrue();
        assertThat(future.isDone()).isTrue();
        assertThat(interrupted.get()).isFalse();

        // There are no running tasks
        assertThat(service.isTerminated()).isTrue();

        // release latch, un-block thread
        latch.countDown();
        // still not interrupted
        assertThat(interrupted.get()).isFalse();
    }

    @Test
    public void shouldInterruptRunningJobs() throws Exception {

        JsonValue config = json(object(field("gracefulStop", false)));

        Heaplet heaplet = new ScheduledExecutorServiceHeaplet();
        ExecutorService service = createExecutorService(heaplet, config);

        AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        service.submit(new ManagedSleepingRunnable(600, state, started, completed));

        // wait for task to start
        started.await();
        // task has not completed yet
        assertThat(state.get()).isNotIn(State.KILLED, State.DONE);

        // Attempt to destroy the heaplet
        // Should exist quickly without killing the task
        heaplet.destroy();

        completed.await();

        // task have not been completed (interrupted)
        assertThat(state.get()).isEqualTo(State.KILLED);
    }

    @DataProvider
    public static Object[][] invalidConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
                { json(object(field("corePoolSize", 0))) },
                { json(object(field("corePoolSize", -15))) },
                { json(object(field("corePoolSize", "${0}"))) },
                { json(object(field("corePoolSize", "${-15}"))) }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidConfigurations",
          expectedExceptions = HeapException.class)
    public void shouldFailWithInvalidConfiguration(JsonValue config) throws Exception {
        createExecutorService(new ScheduledExecutorServiceHeaplet(), config);
    }

    private enum State {
        PENDING, RUN, DONE, KILLED
    }

    private static class ManagedSleepingRunnable implements Runnable {
        private final AtomicReference<State> state;
        private final CountDownLatch started;
        private final CountDownLatch completed;
        private final int timeout;

        ManagedSleepingRunnable(final int timeout,
                                final AtomicReference<State> state,
                                final CountDownLatch started,
                                final CountDownLatch completed) {
            this.timeout = timeout;
            this.state = state;
            this.started = started;
            this.completed = completed;
        }

        @Override
        public void run() {
            state.set(State.RUN);
            started.countDown();
            try {
                Thread.sleep(timeout);
                state.set(State.DONE);
            } catch (InterruptedException e) {
                state.set(State.KILLED);
            } finally {
                completed.countDown();
            }
        }
    }
}
