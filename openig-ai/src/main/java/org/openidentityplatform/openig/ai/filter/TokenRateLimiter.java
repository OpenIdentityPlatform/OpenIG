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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.ai.filter;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


/**
 * Identity-aware, in-memory token-bucket rate limiter for LLM prompt tokens.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 *   "rate": {
 *     "numberOfTokens": 10000,
 *     "duration": "1 minute"
 *   }
 * }</pre>
 */

public class TokenRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(TokenRateLimiter.class);

    private final long numberOfTokens;

    /** Refill window in nanoseconds. */
    private final long windowMs;

    private final LoadingCache<String, Bucket> rateLimiters;

    private final Ticker ticker;


    /**
     * Minimal constructor, with system ticker and no bucket eviction
     *
     * @param numberOfTokens   maximum tokens per window (= burst capacity)
     * @param duration          window size — OpenIG {@link Duration} syntax
     */
    public TokenRateLimiter(long numberOfTokens, Duration duration) {
        this(numberOfTokens, duration, Ticker.systemTicker(), null);
    }

    /**
     * Full constructor, matching the pattern of
     * {@code TokenBucketThrottlingStrategy(Ticker, ScheduledExecutorService, Duration)}.
     *
     * <p>When {@code cleaningInterval} is non-null, a
     * periodic eviction task removes buckets that have been idle for at least one full
     * window, preventing unbounded heap growth in long-running deployments.
     *
     * @param numberOfTokens   maximum tokens per window (= burst capacity)
     * @param duration          window size — OpenIG {@link Duration} syntax
     * @param ticker            monotonic time source; use {@link Ticker#systemTicker()}
     *                          in production and {@code FakeTicker} in tests
     * @param cleaningInterval  eviction period; ignored when executor is {@code null}
     */
    public TokenRateLimiter(long numberOfTokens,
                            Duration duration,
                            Ticker ticker,
                            Duration cleaningInterval) {
        if (numberOfTokens <= 0) {
            throw new IllegalArgumentException("numberOfTokens must be > 0, got: " + numberOfTokens);
        }
        if (duration == null || duration.isUnlimited()) {
            throw new IllegalArgumentException("duration must be a finite positive value");
        }
        long windowMs = duration.to(TimeUnit.MILLISECONDS);
        if (windowMs <= 0) {
            throw new IllegalArgumentException("duration must resolve to > 0 ms, got: " + duration);
        }

        this.numberOfTokens = numberOfTokens;
        this.windowMs       = windowMs;

        this.ticker = ticker;


        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        if (cleaningInterval != null) {
            long intervalMs = cleaningInterval.to(TimeUnit.MILLISECONDS);
            builder.expireAfterAccess(intervalMs, TimeUnit.MILLISECONDS).ticker(this.ticker);
        }
        rateLimiters = builder.build(this::createNewBucket);


    }
    private Bucket createNewBucket(String sub) {

        Bandwidth limit = BandwidthBuilder.builder().capacity(numberOfTokens)
                .refillGreedy(numberOfTokens, java.time.Duration.ofMillis(windowMs))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .withCustomTimePrecision(new TimeMeter() {
                    @Override
                    public long currentTimeNanos() {
                        return ticker.read();
                    }

                    @Override
                    public boolean isWallClockBased() {
                        return false;
                    }
                })
                .build();
    }

    /**
     * Try to consume N tokens (e.g. costly endpoints) for {@code identity}
     */
    public long tryConsume(String sub, long cost) {
        Bucket bucket = rateLimiters.get(sub);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(cost);
        return probe.getNanosToWaitForRefill();
    }


    public long availableTokens(String sub) {
        Bucket bucket = rateLimiters.get(sub);
        if (bucket == null) return numberOfTokens;
        return bucket.getAvailableTokens();
    }

    public void stop() {
        rateLimiters.cleanUp();
        logger.debug("TokenRateLimiter: stopped");
    }
}
