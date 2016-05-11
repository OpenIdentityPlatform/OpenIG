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

package org.forgerock.http.filter.throttling;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rate limiting is implemented as a token bucket strategy
 * that gives us the ability to handle rate limits through a sliding window. Multiple rates can be supported in
 * parallel with the support of a partition key (we first try to find the bucket to use for each incoming request, then
 * we apply the rate limit).
 */
public class TokenBucketThrottlingStrategy implements ThrottlingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketThrottlingStrategy.class);

    private final Ticker ticker;
    private final ConcurrentMap<String, TokenBucket> partitions;
    private final ScheduledFuture<?> cleaningFuture;

    private class CleaningThread implements Runnable {

        @Override
        public void run() {
            final ConcurrentMap<String, TokenBucket> buckets = TokenBucketThrottlingStrategy.this.partitions;
            Iterator<Map.Entry<String, TokenBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TokenBucket> entry = iterator.next();
                TokenBucket tokenBucket = entry.getValue();
                if (tokenBucket.isExpired()) {
                    iterator.remove();
                    logger.trace("Cleaned the partition {}", entry.getKey());
                }
            }
        }

    }

    /**
     * Constructs a new {@link TokenBucketThrottlingStrategy}.
     *
     * @param ticker the {@link Ticker} to use to follow the timeline.
     * @param scheduledExecutor the {@link ScheduledExecutorService} used to schedule cleaning tasks.
     * @param cleaningInterval the interval between 2 cleaning tasks.
     */
    public TokenBucketThrottlingStrategy(Ticker ticker,
                                         ScheduledExecutorService scheduledExecutor,
                                         Duration cleaningInterval) {
        this(ticker, new ConcurrentHashMap<String, TokenBucket>(), scheduledExecutor, cleaningInterval);
    }

    TokenBucketThrottlingStrategy(Ticker ticker,
                                  ConcurrentMap<String, TokenBucket> partitions,
                                  ScheduledExecutorService scheduledExecutor,
                                  Duration cleaningInterval) {
        this.ticker = checkNotNull(ticker);
        this.partitions = checkNotNull(partitions);
        if (cleaningInterval.isZero() || cleaningInterval.compareTo(duration(1, DAYS)) > 0) {
            throw new IllegalArgumentException("Invalid value for cleaningInterval : "
                                                       + "it has to be in the range ]0, 1 day]");
        }
        this.cleaningFuture = scheduledExecutor.scheduleWithFixedDelay(new CleaningThread(),
                                                                       0, // no delay
                                                                       cleaningInterval.getValue(),
                                                                       cleaningInterval.getUnit());
    }

    @Override
    public Promise<Long, NeverThrowsException> throttle(String partitionKey, ThrottlingRate throttlingRate) {
        TokenBucket bucket = selectTokenBucket(partitionKey, throttlingRate);
        logger.trace("Applying rate {} ({} remaining tokens)",
                     bucket.getThrottlingRate(),
                     bucket.getRemainingTokensCount());
        return newResultPromise(bucket.tryConsume());
    }

    private TokenBucket selectTokenBucket(String partitionKey, ThrottlingRate rate) {
        for (;;) {
            TokenBucket previousBucket = partitions.get(partitionKey);
            if (previousBucket == null) {
                TokenBucket newBucket = new TokenBucket(ticker, rate);
                previousBucket = partitions.putIfAbsent(partitionKey, newBucket);
                if (previousBucket == null) {
                    // There was no previous TokenBucket, so go on with that freshly created one
                    return newBucket;
                }
            }
            if (previousBucket.getThrottlingRate().equals(rate)) {
                // Same rate : let's continue with the previous one as it may already be processing some requests
                return previousBucket;
            }
            // The rate definition has changed so try to assign this new TokenBucket
            TokenBucket newBucket = new TokenBucket(ticker, rate);
            if (partitions.replace(partitionKey, previousBucket, newBucket)) {
                return newBucket;
            }
            // The rate definition was not the same but has already been updated,
            // let's loop once more to see if we get more chance.
        }
    }

    @Override
    public void stop() {
        cleaningFuture.cancel(false);
        partitions.clear();
    }

}
