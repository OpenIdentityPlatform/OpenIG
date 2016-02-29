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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.http.filter.throttling;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.UNLIMITED;
import static org.forgerock.util.time.Duration.ZERO;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.FakeTimeService;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThrottlingFilterTest {

    private static final Duration CLEANING_INTERVAL = Duration.duration("5 seconds");

    private ThrottlingFilter filter;

    @AfterMethod
    public void tearDownMethod() throws Exception {
        if (filter != null) {
            filter.stop();
        }
    }

    @DataProvider
    public static Object[][] incorrectCleaningIntervals() {
        //@Checkstyle:off
        return new Object[][]{
                { ZERO },
                { UNLIMITED },
                { duration(25, TimeUnit.HOURS) },
        };
        //@Checkstyle:on
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "incorrectCleaningIntervals")
    public void shouldRefuseIncorrectCleaningInterval(Duration cleaningInterval) throws Exception {
        new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                             TimeService.SYSTEM,
                             cleaningInterval,
                             new StringRequestAsyncFunction(null),
                             throttlingRatePolicy(1, duration("3 seconds")));
    }

    @Test
    public void shouldRespondInternalServerErrorOnNullPartitionKey() throws Exception {
        // Given
        ThrottlingPolicy throttlingPolicy = mock(ThrottlingPolicy.class);
        filter = new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                                      TimeService.SYSTEM,
                                      CLEANING_INTERVAL,
                                      new StringRequestAsyncFunction(null),
                                      throttlingPolicy);

        // When
        Response response = filter.filter(new RootContext(), new Request(), new ResponseHandler(Status.OK)).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        verifyZeroInteractions(throttlingPolicy);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldForwardTheRequestRequestWhenTokenBucketRateChanged() throws Exception {
        // Given
        ConcurrentMap<String, TokenBucket> concurrentMap = mock(ConcurrentMap.class);
        TokenBucket previousTokenBucket = new TokenBucket(TimeService.SYSTEM,
                                                          new ThrottlingRate(1, duration("3 seconds")));
        TokenBucket newTokenBucket = new TokenBucket(TimeService.SYSTEM, new ThrottlingRate(42, duration("5 seconds")));
        final String partitionKey = "foo";
        when(concurrentMap.putIfAbsent(partitionKey, newTokenBucket)).thenReturn(previousTokenBucket,
                                                                                 (TokenBucket) null);
        when(concurrentMap.replace(partitionKey, previousTokenBucket, newTokenBucket)).thenReturn(false);

        filter = new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                                      TimeService.SYSTEM,
                                      CLEANING_INTERVAL,
                                      new StringRequestAsyncFunction(partitionKey),
                                      throttlingRatePolicy(42, duration("5 seconds")),
                                      concurrentMap);

        // When
        Response response = filter.filter(new RootContext(), new Request(), new ResponseHandler(Status.OK)).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseDifferentBucketsWhenUsingValidPartitionKey() throws Exception {
        // arbitrary variables just to define a valid rate of 1 request per 3 seconds.
        final int numberOfRequests = 1;
        final Duration duration = duration("3 seconds");

        // Given
        FakeTimeService time = new FakeTimeService();

        AsyncFunction<ContextAndRequest, String, Exception> partitionKey =
                new AsyncFunction<ContextAndRequest, String, Exception>() {
                    @Override
                    public Promise<String, Exception> apply(ContextAndRequest contextAndRequest) {
                        AttributesContext attributesContext = contextAndRequest.getContext()
                                                                               .asContext(AttributesContext.class);
                        return newResultPromise((String) attributesContext.getAttributes().get("partitionKey"));
                    }
                };

        filter = new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                                      time,
                                      CLEANING_INTERVAL,
                                      partitionKey,
                                      throttlingRatePolicy(numberOfRequests, duration));

        Handler handler = new ResponseHandler(Status.OK);

        Response response;

        // The first request goes through as the bucket "bar-00" is freshly created
        response = filter.filter(attributesContextWithPartitionKey("bar-00"), new Request(), handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);

        // The second request has an equivalent throttling definition (same key and same rate), so it uses the same
        // bucket, which is now empty, so the request can't go through the filter
        response = filter.filter(attributesContextWithPartitionKey("bar-00"), new Request(), handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);

        // The third request does *not* have an equivalent throttling definition (the key differs), so it uses another
        // bucket and thus the request can go through the filter.
        response = filter.filter(attributesContextWithPartitionKey("bar-01"), new Request(), handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    private Context attributesContextWithPartitionKey(String key) {
        AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("partitionKey", key);

        return context;
    }

    @Test
    public void shouldThrottleRequests() throws Exception {
        Handler handler = mock(Handler.class);
        FakeTimeService time = new FakeTimeService();
        filter = new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                                      time,
                                      CLEANING_INTERVAL,
                                      new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")));

        // Timestamp = 0s
        filter.filter(new RootContext(), new Request(), handler);
        // This one has to call the handler as there are enough tokens in the bucket.
        verify(handler).handle(any(Context.class), any(Request.class));

        // Timestamp = 1s
        time.advance(duration("1 seconds"));
        reset(handler);
        Response response = filter.filter(new RootContext(), new Request(), handler).get();
        // This one does not have to be called as there is no token anymore in the bucket.
        verifyZeroInteractions(handler);
        assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);

        // Timestamp = 4s
        time.advance(duration("3 seconds"));
        reset(handler);
        filter.filter(new RootContext(), new Request(), handler);
        // This one has to call the handler as the bucket has been refilled.
        verify(handler).handle(any(Context.class), any(Request.class));
    }

    /**
     * A first request comes in : while it takes some time to process it, another request is coming in and thus has to
     * be processed concurrently. But since the first request consumed the single token from the bucket, the second
     * request won't be allowed to continue.
     */
    @Test
    public void shouldThrottleConcurrentRequests() throws Exception {
        final CountDownLatch pauseHandler = new CountDownLatch(1);
        CountDownLatch waitHandler = new CountDownLatch(1);

        FakeTimeService time = new FakeTimeService();
        filter = new ThrottlingFilter(newSingleThreadScheduledExecutor(),
                                      time,
                                      CLEANING_INTERVAL,
                                      new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")));

        final Handler handler = new LatchHandler(pauseHandler, waitHandler);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                filter.filter(new RootContext(), new Request(), handler);
            }
        };
        Thread thread = new Thread(r);
        thread.setName("Filter for request #1");
        thread.start();
        waitHandler.await(); // Wait here until the handler is executed

        time.advance(duration("2 seconds"));
        try {
            Handler spiedHandler = spy(handler);
            // The second request comes in
            Response response = filter.filter(new RootContext(), new Request(), spiedHandler).get();

            // There is no token anymore in the bucket so the handler does not have to be called.
            verifyZeroInteractions(spiedHandler);
            assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        } finally {
            pauseHandler.countDown(); // Allow the handler to continue its processing
            thread.join();
        }
    }

    /**
     * Utility handler that allows simulation of concurrency.
     */
    static class LatchHandler implements Handler {

        private final CountDownLatch pause;
        private final CountDownLatch signal;

        /**
         * @param pause
         *         this latch will make this handler to pause it processing
         * @param signal
         *         this latch will allow to signal the outside stuff that this handler has been called
         */
        public LatchHandler(CountDownLatch pause, CountDownLatch signal) {
            this.pause = pause;
            this.signal = signal;
        }

        @Override
        public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
            try {
                signal.countDown();
                pause.await();
                return newResponsePromise(new Response(Status.OK));
            } catch (InterruptedException e) {
                return newResponsePromise(newInternalServerError());
            }
        }

    }

    private static class StringRequestAsyncFunction implements AsyncFunction<ContextAndRequest, String, Exception> {

        private final String value;

        public StringRequestAsyncFunction(String value) {
            this.value = value;
        }

        @Override
        public Promise<String, Exception> apply(ContextAndRequest contextAndRequest) {
            return newResultPromise(value);
        }
    }

    private ThrottlingPolicy throttlingRatePolicy(final int numberOfRequests, final Duration duration) {
        return new ThrottlingPolicy() {
            @Override
            public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
                return newResultPromise(new ThrottlingRate(numberOfRequests, duration));
            }
        };
    }

}
