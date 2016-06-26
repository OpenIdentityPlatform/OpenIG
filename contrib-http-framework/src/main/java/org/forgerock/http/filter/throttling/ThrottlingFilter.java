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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
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

    private static final Logger logger = LoggerFactory.getLogger(ThrottlingFilter.class);

    private final AsyncFunction<ContextAndRequest, String, Exception> requestGroupingPolicy;
    private final ThrottlingPolicy throttlingRatePolicy;
    private final ThrottlingStrategy throttlingStrategy;

    /**
     * Constructs a ThrottlingFilter.
     *
     * @param requestGroupingPolicy
     *         the key used to identify the token bucket (must not be {@code null}).
     * @param throttlingRatePolicy
     *         the datasource where to lookup for the rate to apply (must not be {@code null}).
     * @param throttlingStrategy
     *         the throttling strategy to apply.
     */
    public ThrottlingFilter(AsyncFunction<ContextAndRequest, String, Exception> requestGroupingPolicy,
                            ThrottlingPolicy throttlingRatePolicy,
                            ThrottlingStrategy throttlingStrategy) {
        this.requestGroupingPolicy = checkNotNull(requestGroupingPolicy);
        this.throttlingRatePolicy = checkNotNull(throttlingRatePolicy);
        this.throttlingStrategy = checkNotNull(throttlingStrategy);
    }

    /**
     * Stops this filter and frees the resources.
     */
    public void stop() {
        throttlingStrategy.stop();
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
                                logger.error("Did not expect a null value for the partition key after "
                                                     + "having evaluated the function");
                                return newResponsePromise(newInternalServerError());
                            }

                            ThrottlingRate throttlingRate = throttlingRatePromise.get();
                            if (throttlingRate == null) {
                                logger.trace("No throttling throttlingRate to apply.");
                                // Can't apply any restrictions, continue the chain with no restriction
                                return next.handle(context, request);
                            }

                            return throttlingStrategy.throttle(partitionKey, throttlingRate)
                                                     .thenAsync(applyThrottlingDecision());
                        } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
                            return newResponsePromise(newInternalServerError(e));
                        }
                    }

                    private AsyncFunction<Long, Response, NeverThrowsException> applyThrottlingDecision() {
                        return new AsyncFunction<Long, Response, NeverThrowsException>() {
                            @Override
                            public Promise<? extends Response, ? extends NeverThrowsException> apply(Long delay) {
                                if (delay <= 0) {
                                    return next.handle(context, request);
                                }
                                return newResponsePromise(tooManyRequests(delay));
                            }
                        };
                    }

                    private Response tooManyRequests(long delay) {
                        // http://tools.ietf.org/html/rfc6585#section-4
                        Response response = new Response(Status.TOO_MANY_REQUESTS);
                        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
                        response.getHeaders().add("Retry-After", computeRetryAfter(delay));
                        return response;
                    }

                    private String computeRetryAfter(final long delay) {
                        // According to the Javadoc of TimeUnit.convert :
                        // 999_999_999 ns => 0 sec, but we want to answer 1 sec.
                        //   999_999_999 ns + 999_999_999 ns = 1_000_000_998 ns => 1 second
                        // 1_000_000_000 ns + 999_999_999 ns = 1_000_000_999 ns => 1 second
                        // 1_000_000_001 ns + 999_999_999 ns = 2_000_000_000 ns => 2 seconds
                        return Long.toString(SECONDS.convert(delay + 999_999_999L, NANOSECONDS));
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
