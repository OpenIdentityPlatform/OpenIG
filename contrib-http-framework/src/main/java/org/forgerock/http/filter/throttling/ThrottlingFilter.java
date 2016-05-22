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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter applies a rate limitation to incoming requests : over the limit requests will be rejected with a 429
 * (Too Many Requests) response, others will pass through. The rate limiting is implemented as a token bucket strategy
 * that gives us the ability to handle rate limits through a sliding window. Multiple rates can be supported in
 * parallel with the support of a partition key (we first try to find the bucket to use for each incoming request, then
 * we apply the rate limit). Note that if no rate definition is found, this filter let the request goes through.
 */
public class ThrottlingFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottlingFilter.class);

    private final TimeService time;
    private final AsyncFunction<ContextAndRequest, String, Exception> requestGroupingPolicy;
    private final ThrottlingPolicy throttlingRatePolicy;
    private final ConcurrentMap<String, TokenBucket> buckets;
    private final ScheduledFuture<?> cleaningFuture;

    private class CleaningThread implements Runnable {

        @Override
        public void run() {
            final ConcurrentMap<String, TokenBucket> buckets = ThrottlingFilter.this.buckets;
            Iterator<Map.Entry<String, TokenBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TokenBucket> entry = iterator.next();
                TokenBucket tokenBucket = entry.getValue();
                if (tokenBucket.isExpired()) {
                    iterator.remove();
                    ThrottlingFilter.LOGGER.trace("Cleaned the partition {}", entry.getKey());
                }
            }
        }

    }

    /**
     * Constructs a ThrottlingFilter.
     *
     * @param scheduledExecutor
     *         the scheduled executor service used to schedule house cleaning tasks (must not be {@code null}).
     * @param time
     *         the time service (must not be {@code null}).
     * @param cleaningInterval
     *         the interval to wait for cleaning outdated buckets (must not be {@code null} and in the range
     *         ]0, 1 day]).
     * @param requestGroupingPolicy
     *         the key used to identify the token bucket (must not be {@code null}).
     * @param throttlingRatePolicy
     *         the datasource where to lookup for the rate to apply (must not be {@code null}).
     */
    public ThrottlingFilter(ScheduledExecutorService scheduledExecutor,
                            TimeService time,
                            Duration cleaningInterval,
                            AsyncFunction<ContextAndRequest, String, Exception> requestGroupingPolicy,
                            ThrottlingPolicy throttlingRatePolicy) {
        this(scheduledExecutor,
             time,
             cleaningInterval,
             requestGroupingPolicy,
             throttlingRatePolicy,
             new ConcurrentHashMap<String, TokenBucket>());
    }

    @VisibleForTesting
    ThrottlingFilter(ScheduledExecutorService scheduledExecutor,
                     TimeService time,
                     Duration cleaningInterval,
                     AsyncFunction<ContextAndRequest, String, Exception> requestGroupingPolicy,
                     ThrottlingPolicy throttlingRatePolicy,
                     ConcurrentMap<String, TokenBucket> buckets) {
        this.time = checkNotNull(time);
        this.requestGroupingPolicy = checkNotNull(requestGroupingPolicy);
        this.throttlingRatePolicy = checkNotNull(throttlingRatePolicy);
        this.buckets = checkNotNull(buckets);
        if (cleaningInterval.isZero() || cleaningInterval.compareTo(duration(1, DAYS)) > 0) {
            throw new IllegalArgumentException("Invalid value for cleaningInterval : "
                                                       + "it has to be in the range ]0, 1 day]");
        }

        cleaningFuture = scheduledExecutor.scheduleWithFixedDelay(new CleaningThread(),
                                                                  0, // no delay
                                                                  cleaningInterval.getValue(),
                                                                  cleaningInterval.getUnit());
    }

    /**
     * Stops this filter and frees the resources.
     */
    public void stop() {
        cleaningFuture.cancel(false);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final Promise<ThrottlingRate, Exception> throttlingRatePromise =
                throttlingRatePolicy.lookup(context, request);
        final Promise<String, Exception> partitionKeyPromise =
                newResultPromise(new ContextAndRequest(context, request))
                        .thenAsync(requestGroupingPolicy);

        return whenAllDone(throttlingRatePromise, partitionKeyPromise)
                .thenAsync(new AsyncFunction<Void, Response, NeverThrowsException>() {
                    @Override
                    public Promise<? extends Response, ? extends NeverThrowsException> apply(Void ignore) {
                        try {
                            String partitionKey = partitionKeyPromise.get();
                            if (partitionKey == null) {
                                LOGGER.error("Did not expect a null value for the partition key after "
                                                     + "having evaluated the function");
                                return newResponsePromise(newInternalServerError());
                            }

                            ThrottlingRate throttlingRate = throttlingRatePromise.get();
                            if (throttlingRate == null) {
                                LOGGER.trace("No throttling throttlingRate to apply.");
                                // Can't apply any restrictions, continue the chain with no restriction
                                return next.handle(context, request);
                            }

                            return throttle(selectTokenBucket(partitionKey, throttlingRate));
                        } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
                            return newResponsePromise(newInternalServerError(e));
                        }
                    }

                    /**
                     * Select the {@code TokenBucket} to use : either the one passed as parameter or an
                     * existing one if there was
                     * already a "session" in progress.
                     * If {@code requestGroupingPolicy} is null, then {@literal null} is returned.
                     */
                    private TokenBucket selectTokenBucket(String partitionKey, ThrottlingRate rate) {
                        TokenBucket tokenBucket = new TokenBucket(time, rate);
                        for (;;) {
                            TokenBucket previous = buckets.putIfAbsent(partitionKey, tokenBucket);
                            if (previous == null) {
                                // There was no previous TokenBucket, so go on with that freshly created one
                                return tokenBucket;
                            } else if (previous.isEquivalent(tokenBucket)) {
                                // Let's continue with the previous one as it may already be processing some requests
                                return previous;
                            } else if (buckets.replace(partitionKey, previous, tokenBucket)) {
                                // The rate definition has changed so try to assign this new TokenBucket
                                return tokenBucket;
                            } else {
                                // The rate definition was not the same but has already been updated,
                                // let's loop once more to see if we get more chance.
                            }
                        }
                    }

                    private Promise<Response, NeverThrowsException> throttle(TokenBucket bucket) {
                        if (bucket == null) {
                            LOGGER.error("No token bucket to use");
                            return newResponsePromise(newInternalServerError());
                        }
                        LOGGER.trace("Applying rate {} requests per {} ms ({} remaining tokens)",
                                     bucket.getCapacity(),
                                     bucket.getDurationInMillis(),
                                     bucket.getRemainingTokensCount());
                        final long delay = bucket.tryConsume();
                        if (delay <= 0) {
                            return next.handle(context, request);
                        } else {
                            return newResponsePromise(tooManyRequests(delay));
                        }
                    }

                    private Response tooManyRequests(long delay) {
                        // http://tools.ietf.org/html/rfc6585#section-4
                        Response response = new Response(Status.TOO_MANY_REQUESTS);
                        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
                        response.getHeaders().add("Retry-After", computeRetryAfter(delay));
                        return response;
                    }

                    private String computeRetryAfter(final long delay) {
                        // According to the Javadoc of TimeUnit.convert : 999 ms => 0 sec, but we want to answer 1 sec.
                        //  999 + 999 = 1998 => 1 second
                        // 1000 + 999 = 1999 => 1 second
                        // 1001 + 999 = 2000 => 2 seconds
                        return Long.toString(SECONDS.convert(delay + 999L, MILLISECONDS));
                    }

                });
    }

    private static Promise<Void, NeverThrowsException> whenAllDone(final Promise<?, ?>... promises) {
        // Fast exit
        if (promises == null || promises.length == 0) {
            return newResultPromise(null);
        }

        final AtomicInteger remaining = new AtomicInteger(promises.length);
        final PromiseImpl<Void, NeverThrowsException> composite = PromiseImpl.create();

        for (final Promise<?, ?> promise : promises) {
            promise.thenAlways(new Runnable() {
                @Override
                public void run() {
                    if (remaining.decrementAndGet() == 0) {
                        composite.handleResult(null);
                    }
                }
            });
        }

        return composite;
    }

}
