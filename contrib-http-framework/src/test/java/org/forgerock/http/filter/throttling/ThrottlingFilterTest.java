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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThrottlingFilterTest {

    private ThrottlingFilter filter;

    @AfterMethod
    public void tearDownMethod() throws Exception {
        if (filter != null) {
            filter.stop();
        }
    }

    @Test
    public void shouldRespondInternalServerErrorOnNullPartitionKey() throws Exception {
        // Given
        filter = new ThrottlingFilter(new StringRequestAsyncFunction(null),
                                      throttlingRatePolicy(1, duration("3 seconds")),
                                      mock(ThrottlingStrategy.class));

        // When
        Response response = filter.filter(new RootContext(), new Request(), new ResponseHandler(Status.OK)).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void shouldForwardTheRequestWhenTheRateIsNull() throws Exception {
        // Given
        ThrottlingPolicy throttlingRatePolicy = new ThrottlingPolicy() {
            @Override
            public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
                return newResultPromise(null);
            }
        };
        ThrottlingStrategy throttlingStrategy = mock(ThrottlingStrategy.class);
        filter = new ThrottlingFilter(new StringRequestAsyncFunction("foo"), throttlingRatePolicy, throttlingStrategy);

        // When
        Handler handler = mock(Handler.class);
        Context context = mock(Context.class);
        Request request = new Request();
        filter.filter(context, request, handler);

        // Then
        verify(handler).handle(eq(context), eq(request));
        verifyNoMoreInteractions(throttlingStrategy);
    }

    @Test
    public void shouldForwardTheRequestWhenTheThrottlingStrategyAccepts() throws Exception {
        // Given
        ThrottlingStrategy throttlingStrategy = mock(ThrottlingStrategy.class);
        when(throttlingStrategy.throttle(eq("foo"), any(ThrottlingRate.class)))
                .thenReturn(Promises.<Long, NeverThrowsException>newResultPromise(0L));
        filter = new ThrottlingFilter(new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")),
                                      throttlingStrategy);

        // When
        Response response = filter.filter(new RootContext(), new Request(), new ResponseHandler(Status.OK)).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void shouldRefuseTheRequestWhenTheThrottlingStrategyDenies() throws Exception {
        // Given
        ThrottlingStrategy throttlingStrategy = mock(ThrottlingStrategy.class);
        when(throttlingStrategy.throttle(eq("foo"), any(ThrottlingRate.class)))
                .thenReturn(Promises.<Long, NeverThrowsException>newResultPromise(1L));
        filter = new ThrottlingFilter(new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")),
                                      throttlingStrategy);

        // When
        Response response = filter.filter(new RootContext(), new Request(), new ResponseHandler(Status.OK)).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
    }

    @Test
    public void shouldSetTheResponseHeaderRetryAfterWhenTooManyRequests() throws Exception {
        ThrottlingStrategy throttlingStrategy = mock(ThrottlingStrategy.class);
        when(throttlingStrategy.throttle(anyString(),
                                         any(ThrottlingRate.class)))
                .thenReturn(Promises.<Long, NeverThrowsException>newResultPromise(1L)); // refuse every request
        filter = new ThrottlingFilter(new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")),
                                      throttlingStrategy);

        Response response = this.filter.filter(new RootContext(), new Request(), null).get();
        assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    }

    /**
     * A first request comes in : while it takes some time to process it, another request is coming in and thus has to
     * be processed concurrently. But since the first request consumed the single token from the bucket, the second
     * request won't be allowed to continue.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void shouldThrottleConcurrentRequests() throws Exception {
        final CountDownLatch pauseHandler = new CountDownLatch(1);
        CountDownLatch waitHandler = new CountDownLatch(1);

        ThrottlingStrategy throttlingStrategy = mock(ThrottlingStrategy.class);
        when(throttlingStrategy.throttle(eq("foo"), any(ThrottlingRate.class)))
                .thenReturn(Promises.<Long, NeverThrowsException>newResultPromise(0L),
                            Promises.<Long, NeverThrowsException>newResultPromise(1L));

        filter = new ThrottlingFilter(new StringRequestAsyncFunction("foo"),
                                      throttlingRatePolicy(1, duration("3 seconds")),
                                      throttlingStrategy);

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

        try {
            Handler spiedHandler = spy(handler);
            // The second request comes in
            Response response = filter.filter(new RootContext(), new Request(), spiedHandler).get();

            // The throttling strategy refuses it so the handler does not have to be called and must return a 429.
            verifyNoMoreInteractions(spiedHandler);
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
    private static class LatchHandler implements Handler {

        private final CountDownLatch pause;
        private final CountDownLatch signal;

        /**
         * @param pause
         *         this latch will make this handler to pause it processing
         * @param signal
         *         this latch will allow to signal the outside stuff that this handler has been called
         */
        LatchHandler(CountDownLatch pause, CountDownLatch signal) {
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

        StringRequestAsyncFunction(String value) {
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
