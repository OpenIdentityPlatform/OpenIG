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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.util.time.Duration.duration;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.guava.common.cache.CacheBuilder;
import org.forgerock.guava.common.cache.CacheLoader;
import org.forgerock.guava.common.cache.CacheStats;
import org.forgerock.guava.common.cache.LoadingCache;
import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Responses;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * This filter allows to limit the output rate to the specified handler. If the output rate is over, there a response
 * with status 429 (Too Many Requests) is sent.
 */
public class ThrottlingFilter extends GenericHeapObject implements Filter {

    static final String DEFAULT_PARTITION_KEY = "";

    private final LoadingCache<String, TokenBucket> buckets;
    private final Expression<String> partitionKey;

    /**
     * Constructs a ThrottlingFilter.
     *
     * @param time
     *            the time service.
     * @param numberOfRequests
     *            the maximum of requests that can be filtered out during the duration.
     * @param duration
     *            the duration of the sliding window.
     * @param partitionKey
     *            the optional expression that tells in which bucket we have to take some token to count the output
     *            rate.
     */
    public ThrottlingFilter(final TimeService time,
                            final int numberOfRequests,
                            final Duration duration,
                            final Expression<String> partitionKey) {
        Reject.ifNull(partitionKey);
        this.buckets = setupBuckets(time, numberOfRequests, duration);
        this.partitionKey = partitionKey;

        // Force to load the TokenBucket of the DEFAULT_PARTITION_KEY in order to validate the input parameters.
        // If the parameters are not valid that will throw some unchecked exceptions.
        buckets.getUnchecked(DEFAULT_PARTITION_KEY);
    }

    /**
     * Returns the statistics of the underlying cache. This method must only be used for unit testing.
     *
     * @return the cache statistics
     */
    CacheStats getBucketsStats() {
        return buckets.stats();
    }

    private LoadingCache<String, TokenBucket> setupBuckets(final TimeService time,
                                                           final int numberOfRequests,
                                                           final Duration duration) {

        CacheLoader<String, TokenBucket> loader = new CacheLoader<String, TokenBucket>() {
            @Override
            public TokenBucket load(String key) {
                return new TokenBucket(time, numberOfRequests, duration);
            }
        };

        // Wrap our TimeService so we can play with the time in our tests
        Ticker ticker = new Ticker() {

            @Override
            public long read() {
                return NANOSECONDS.convert(time.now(), MILLISECONDS);
            }
        };
        // Let's give some arbitrary delay for the eviction
        long expire = duration.to(MILLISECONDS) + 3;
        return CacheBuilder.newBuilder()
                           .ticker(ticker)
                           .expireAfterAccess(expire, MILLISECONDS)
                           .recordStats()
                           .build(loader);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final Exchange exchange = context.asContext(Exchange.class);

        final String key = partitionKey.eval(exchange);
        if (key == null) {
            logger.error("Did not expect a null value for the partitionKey after evaluated the expression : "
                    + partitionKey);
            return Promises.newResultPromise(Responses.newInternalServerError());
        }

        return filter(buckets.getUnchecked(key), context, request, next);
    }

    private Promise<Response, NeverThrowsException> filter(TokenBucket bucket,
                                                           Context context,
                                                           Request request,
                                                           Handler next) {
        final long delay = bucket.tryConsume();
        if (delay <= 0) {
            return next.handle(context, request);
        } else {
            // http://tools.ietf.org/html/rfc6585#section-4
            Response result = new Response(Status.TOO_MANY_REQUESTS);
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
            result.getHeaders().add("Retry-After", computeRetryAfter(delay));
            return Promises.newResultPromise(result);
        }
    }

    private String computeRetryAfter(final long delay) {
        // According to the Javadoc of TimeUnit.convert : 999 ms => 0 sec, but we want to answer 1 sec.
        //  999 + 999 = 1998 => 1 second
        // 1000 + 999 = 1999 => 1 second
        // 1001 + 999 = 2000 => 2 seconds
        return Long.toString(SECONDS.convert(delay + 999L, MILLISECONDS));
    }

    /**
     * Creates and initializes a throttling filter in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            TimeService time = heap.get(Keys.TIME_SERVICE_HEAP_KEY, TimeService.class);

            JsonValue rate = config.get("rate").required();

            Integer numberOfRequests = rate.get("numberOfRequests").required().asInteger();
            Duration duration = duration(rate.get("duration").required().asString());
            Expression<String> partitionKey = asExpression(config.get("partitionKey").defaultTo(DEFAULT_PARTITION_KEY),
                                                           String.class);

            return new ThrottlingFilter(time, numberOfRequests, duration, partitionKey);
        }
    }
}
