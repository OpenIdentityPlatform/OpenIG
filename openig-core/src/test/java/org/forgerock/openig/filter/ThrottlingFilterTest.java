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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.forgerock.guava.common.util.concurrent.UncheckedExecutionException;
import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThrottlingFilterTest {

    private static final Expression<String> DEFAULT_PARTITION_EXPR;

    static {
        try {
            DEFAULT_PARTITION_EXPR = Expression.valueOf(ThrottlingFilter.DEFAULT_PARTITION_KEY, String.class);
        } catch (ExpressionException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test(expectedExceptions = { UncheckedExecutionException.class })
    public void shouldFailWithIncorrectNumberOfRequests() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        new ThrottlingFilter(time, -1, mock(Duration.class), DEFAULT_PARTITION_EXPR);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void shouldFailWithIncorrectDuration() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        new ThrottlingFilter(time, 1, duration("unlimited"), DEFAULT_PARTITION_EXPR);
    }

    @Test
    public void shouldUseDifferentBucketsWhenUsingValidPartitionKey() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        Expression<String> expr = Expression.valueOf("${matches(exchange.foo, 'bar-00') ?'bucket-00' :''}",
                                                     String.class);
        ThrottlingFilter filter = new ThrottlingFilter(time, 1, duration("3 seconds"), expr);

        Handler handler = new ResponseHandler(Status.OK);

        // The time does not need to advance
        Exchange exchange = new Exchange();
        Promise<Response, NeverThrowsException> promise;

        exchange.put("foo", "bar-00");
        promise = filter.filter(exchange, new Request(), handler);
        assertThat(promise.get().getStatus()).isEqualTo(Status.OK);

        exchange.put("foo", "bar-00");
        promise = filter.filter(exchange, new Request(), handler);
        assertThat(promise.get().getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);

        exchange.put("foo", "bar-01");
        promise = filter.filter(exchange, new Request(), handler);
        assertThat(promise.get().getStatus()).isEqualTo(Status.OK);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void shouldFailBecauseNullExpressionIsInvalid() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        new ThrottlingFilter(time, 1, duration("3 seconds"), null);
    }

    @Test
    public void shouldUseDefaultValueWithExpressionEvaluatingNull() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        Expression<String> expr = Expression.valueOf("${exchange.bar}",
                                                     String.class);
        ThrottlingFilter filter = new ThrottlingFilter(time, 1, duration("3 seconds"), expr);

        Handler handler = new ResponseHandler(Status.OK);

        // The time does not need to advance
        Exchange exchange = new Exchange();
        Promise<Response, NeverThrowsException> promise;

        promise = filter.filter(exchange, new Request(), handler);
        assertThat(promise.get().getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void shouldThrottleRequests() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        ThrottlingFilter filter = new ThrottlingFilter(time, 1, duration("3 seconds"), DEFAULT_PARTITION_EXPR);

        // This one has to call the handler as there are enough tokens in the bucket.
        Handler handler1 = mock(Handler.class, "handler1");
        filter.filter(mock(Context.class), new Request(), handler1);
        verify(handler1).handle(any(Context.class), any(Request.class));

        time.advance(duration("2 seconds"));
        // This one does not have to be called as there is no token anymore in the bucket.
        Handler handler2 = mock(Handler.class, "handler2");
        Promise<Response, NeverThrowsException> promise2 = filter.filter(mock(Context.class),
                                                                         new Request(),
                                                                         handler2);
        verifyZeroInteractions(handler2);
        assertThat(promise2.get().getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);

        time.advance(duration("4 seconds"));
        // This one has to call the handler as the bucket has been refilled.
        Handler handler3 = mock(Handler.class, "handler3");
        filter.filter(mock(Context.class), new Request(), handler3);
        verify(handler3).handle(any(Context.class), any(Request.class));
    }

    @Test
    public void shouldEvictObsoleteBucket() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        ThrottlingFilter filter = new ThrottlingFilter(time, 1, duration("3 seconds"), DEFAULT_PARTITION_EXPR);
        Context context = mock(Context.class);

        // Call the filter in order to trigger the bucket creation
        Handler handler1 = mock(Handler.class, "handler1");
        filter.filter(context, new Request(), handler1);
        verify(handler1).handle(any(Exchange.class), any(Request.class));

        time.advance(duration("4 seconds"));

        // After 3 seconds, the previously bucket should be discarded, force a new call so that the cache will clean its
        // internal structure
        Handler handler2 = mock(Handler.class, "handler2");
        filter.filter(context, new Request(), handler2);
        verify(handler2).handle(any(Exchange.class), any(Request.class));

        assertThat(filter.getBucketsStats().evictionCount()).isEqualTo(1);
    }

    @Test
    public void shouldNotEvictAccessedBucket() throws Exception {
        FakeTimeService time = new FakeTimeService(0);
        ThrottlingFilter filter = new ThrottlingFilter(time, 2, duration("3 seconds"), DEFAULT_PARTITION_EXPR);
        Context context = mock(Context.class);
        Request request;

        // Here we access the same bucket every 2 seconds : enough time to add a refilled token but not enough for the
        // bucket to be evicted.
        Handler handler1 = mock(Handler.class, "handler1");
        request = new Request();
        filter.filter(context, request, handler1);
        verify(handler1).handle(context, request);

        time.advance(duration("2 seconds"));
        Handler handler2 = mock(Handler.class, "handler2");
        request = new Request();
        filter.filter(context, request, handler2);
        verify(handler2).handle(context, request);
        assertThat(filter.getBucketsStats().evictionCount()).isEqualTo(0);

        time.advance(duration("2 seconds"));
        Handler handler3 = mock(Handler.class, "handler3");
        request = new Request();
        filter.filter(context, request, handler3);
        verify(handler3).handle(context, request);
        assertThat(filter.getBucketsStats().evictionCount()).isEqualTo(0);
    }

    @Test
    public void shouldThrottleConcurrentRequests() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        FakeTimeService time = new FakeTimeService(0);
        final ThrottlingFilter filter = new ThrottlingFilter(time, 1, duration("3 seconds"), DEFAULT_PARTITION_EXPR);

        // This one has to be called as there are enough tokens in the bucket.
        final Handler handler1 = new LatchHandler(latch1, latch2);

        Runnable r = new Runnable() {

            @Override
            public void run() {
                filter.filter(mock(Context.class), new Request(), handler1);
            }
        };
        Thread t1 = new Thread(r);
        t1.setName("Filter for request #1");
        t1.start();
        latch2.await();

        time.advance(duration("2 seconds"));
        try {
            // This one does not have to be called as there no token anymore in the bucket.
            Handler handler2 = mock(Handler.class, "handler2");
            Promise<Response, NeverThrowsException> promise2 = filter.filter(mock(Context.class),
                                                                             new Request(),
                                                                             handler2);

            verifyZeroInteractions(handler2);

            Response response = promise2.get(20, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        } finally {
            latch1.countDown();
            t1.join();
        }
    }

    private static class LatchHandler implements Handler {

        private CountDownLatch latch1;
        private CountDownLatch latch2;

        public LatchHandler(CountDownLatch latch1, CountDownLatch latch2) {
            this.latch1 = latch1;
            this.latch2 = latch2;
        }

        @Override
        public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
            try {
                latch2.countDown();
                latch1.await();
                return null;
            } catch (InterruptedException e) {
                return Promises.newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR));
            }
        }

    }

}
