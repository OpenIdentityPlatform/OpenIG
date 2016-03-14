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

package org.forgerock.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.UNLIMITED;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThreadSafeCacheTest {

    public static final Duration DEFAULT_CACHE_TIMEOUT = duration("30 seconds");
    public static final int NUMBER_OF_ENTRIES = 10;
    public static final int NUMBER_OF_THREADS = 20;
    public static final int INVOCATION_COUNT = 10000;

    private ThreadSafeCache<Integer, Integer> cache;

    private FakeTimeService time;

    private ScheduledExecutorService executorService;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        time = new FakeTimeService(0);
        executorService = spy(new FakeScheduledExecutorService(time));
        cache = new ThreadSafeCache<>(executorService);
        cache.setDefaultTimeout(DEFAULT_CACHE_TIMEOUT);
    }

    @Test
    public void shouldNotComputeValueMoreThanTenTimes() throws Exception {

        // This "stress" test ensure that the cache only compute each entry once
        // We register a lot of cache calls in an executor service to ensure that multiple Threads are used to
        // perform the operations.
        // Each operation generates a random number between 0 and 10 (that are the possible "slots" in the cache) and
        // tries to get the cached value associated to this slot. Each operation waits for a little time to ensure
        // some overlap between tasks.
        // In order to test that the cached values are created only once, we track the number of Callable invocation.
        // At the end of the test, the number of time we created a value should be equal to the number of slots in
        // the cache.

        final Random random = new Random();
        final AtomicInteger count = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        // Register n cache call...
        for (int i = 0; i < INVOCATION_COUNT; i++) {
            executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    // Randomly select a cache key
                    final int value = Math.abs(random.nextInt() % NUMBER_OF_ENTRIES);

                    // Get the key value
                    cache.getValue(value, new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            // Wait for a little time
                            Thread.sleep(value * 5);
                            // Increment the counter of real value creation calls
                            count.incrementAndGet();
                            return value;
                        }
                    });
                    return null;
                }
            });

        }

        // Stop the executor and wait for termination
        executorService.shutdown();
        executorService.awaitTermination(20L, TimeUnit.SECONDS);

        // Each entry should only be computed once
        assertThat(count.get()).isEqualTo(NUMBER_OF_ENTRIES);
    }

    @Test
    public void shouldRegisterAnExpirationCallbackWithAppropriateDuration() throws Exception {
        cache.getValue(42, getCallable());

        verify(executorService).schedule(anyRunnable(),
                                         eq(DEFAULT_CACHE_TIMEOUT.getValue()),
                                         eq(DEFAULT_CACHE_TIMEOUT.getUnit()));
    }

    @Test
    public void shouldOverrideDefaultTimeout() throws Exception {
        final Duration lowerDuration = duration("10 seconds");
        cache.getValue(42, getCallable(), expire(lowerDuration));

        verify(executorService).schedule(anyRunnable(),
                                         eq(lowerDuration.getValue()),
                                         eq(lowerDuration.getUnit()));
    }

    @Test
    public void shouldNotCacheTheValueWhenTimeoutIsZero() throws Exception {
        cache.getValue(42, getCallable(), expire(Duration.ZERO));

        assertThat(cache.size()).isEqualTo(0);
    }

    @DataProvider
    private static Object[][] timeoutFunctionsNotCacheable() {
        // @formatter:off
        return new Object[][]{
            {
                new AsyncFunction<Integer, Duration, Exception>() {
                    @Override
                    public Promise<Duration, Exception> apply(Integer value) throws Exception {
                        throw new Exception("Boom");
                    }
                }
            },
            {
                new AsyncFunction<Integer, Duration, Exception>() {
                    @Override
                    public Promise<Duration, Exception> apply(Integer value) throws Exception {
                        return newRuntimeExceptionPromise(new RuntimeException("Boom"));
                    }
                }
            },
            {
                new AsyncFunction<Integer, Duration, Exception>() {
                    @Override
                    public Promise<Duration, Exception> apply(Integer value) throws Exception {
                        return newExceptionPromise(new Exception("Boom"));
                    }
                }
            }
        };
        // @formatter:on
    }

    @Test(dataProvider = "timeoutFunctionsNotCacheable")
    public void shouldNotCacheWithTheseTimeoutFunctions(AsyncFunction<Integer, Duration, Exception> timeoutFunction)
            throws Exception {
        Callable<Integer> callable = spy(getCallable());

        // First call : the timeout function fails with an RuntimeException : the value should not have been cached
        cache.getValue(42, callable, timeoutFunction);

        // Second call with the same Callable
        cache.getValue(42, callable);

        // since the value was not cached previously, then
        verify(callable, times(2)).call();
    }

    @Test
    public void shouldNotScheduleExpirationWhenTimeoutIsUnlimited() throws Exception {
        cache.getValue(42, getCallable(), expire(UNLIMITED));
        verifyZeroInteractions(executorService);
    }

    @DataProvider
    private Object[][] durations() {
        return new Object[][] {
            { UNLIMITED },
            { duration(3, TimeUnit.MINUTES) },
            { duration(1, TimeUnit.DAYS) },
        };
    }

    @Test(dataProvider = "durations")
    public void shouldNotCacheMoreThanTheMaxTimeout(final Duration timeout) throws Exception {
        cache.setMaxTimeout(duration(3, TimeUnit.MINUTES));
        cache.getValue(42, getCallable(), expire(timeout));

        verify(executorService).schedule(anyRunnable(), eq(3L), eq(TimeUnit.MINUTES));
    }

    @Test
    public void shouldCacheLessThanTheMaxTimeout() throws Exception {
        cache.setMaxTimeout(duration(3, TimeUnit.MINUTES));
        cache.getValue(42, getCallable(), expire(duration(42, TimeUnit.SECONDS)));

        verify(executorService).schedule(anyRunnable(), eq(42L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void shouldCancelTheExpirationTaskWhenClearingTheCache() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        ScheduledExecutorService executorService = new FakeScheduledExecutorService(time);
        cache = new ThreadSafeCache<>(executorService);
        final int key = 42;
        cache.getValue(key, getCallable(), expire(duration(5, TimeUnit.SECONDS)));
        time.advance(2, TimeUnit.SECONDS);
        cache.clear();

        // Insert another value for the same key
        cache.getValue(key, getCallable(), expire(Duration.UNLIMITED));
        time.advance(4, TimeUnit.SECONDS);

        // We are now 6 seconds after the first value for the key 42 was cached
        // but we inserted a new value for the same key with an unlimited duration so getting the value for 42 should
        // not call the Callable.
        Callable<Integer> callable = spy(getCallable());
        cache.getValue(key, callable, expire(Duration.UNLIMITED));
        verify(callable, never()).call();
    }

    @Test
    public void shouldCancelTheExpirationTaskWhenEvictingAnEntry() throws Exception {
        cache = new ThreadSafeCache<>(executorService);
        final int key = 42;
        cache.getValue(key, getCallable(), expire(duration(5, TimeUnit.SECONDS)));
        time.advance(2, TimeUnit.SECONDS);
        cache.evict(key);

        // Insert another value for the same key
        cache.getValue(key, getCallable(), expire(Duration.UNLIMITED));
        time.advance(4, TimeUnit.SECONDS);

        // We are now 6 seconds after the first value for the key 42 was cached
        // but we inserted a new value for the same key with an unlimited duration so getting the value for 42 should
        // not call the Callable.
        Callable<Integer> callable = spy(getCallable());
        cache.getValue(key, callable, expire(Duration.UNLIMITED));
        verify(callable, never()).call();
    }

    private static Runnable anyRunnable() {
        return any(Runnable.class);
    }

    private Callable<Integer> getCallable() {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 404;
            }
        };
    }

    private AsyncFunction<Integer, Duration, Exception> expire(final Duration duration) {
        return new AsyncFunction<Integer, Duration, Exception>() {

            @Override
            public Promise<Duration, Exception> apply(Integer ignore) throws Exception {
                return newResultPromise(duration);
            }
        };
    }

    private static Promise<Duration, Exception> newRuntimeExceptionPromise(final RuntimeException runtimeException) {
        // Cannot create a RuntimeExceptionPromise in another way
        return Promises.<Duration, Exception>newResultPromise(null)
                .then(new Function<Duration, Duration, Exception>() {
                    @Override
                    public Duration apply(Duration value) throws Exception {
                        throw runtimeException;
                    }
                });
    }
}
