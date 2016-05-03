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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This throttling algorithm is implemented to output a constant rate with no possible burst.
 */
public class ConstantThrottlingStrategy implements ThrottlingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ConstantThrottlingStrategy.class);

    private final Ticker ticker;
    // Contains the partitions and their associated expiration timestamps
    private final ConcurrentMap<String, AtomicLong> partitions;
    private final ScheduledFuture<?> cleaningFuture;

    private class CleaningThread implements Runnable {

        @Override
        public void run() {
            long now = ConstantThrottlingStrategy.this.ticker.read();
            final ConcurrentMap<String, AtomicLong> partition = ConstantThrottlingStrategy.this.partitions;
            Iterator<Map.Entry<String, AtomicLong>> iterator = partition.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AtomicLong> entry = iterator.next();
                AtomicLong expirationTimestamp = entry.getValue();
                if (isExpired(expirationTimestamp.get(), now)) {
                    iterator.remove();
                    logger.trace("Cleaned the partition {}", entry.getKey());
                }
            }
        }

    }

    /**
     * Constructs a new {@link ConstantThrottlingStrategy}.
     *
     * @param ticker the ticker to use to follow the timeline.
     * @param scheduledExecutor the {@link ScheduledExecutorService} used to schedule cleaning tasks.
     * @param cleaningInterval the interval between 2 cleaning tasks.
     */
    public ConstantThrottlingStrategy(Ticker ticker,
                                      ScheduledExecutorService scheduledExecutor,
                                      Duration cleaningInterval) {
        this(ticker, new ConcurrentHashMap<String, AtomicLong>(), scheduledExecutor, cleaningInterval);
    }

    ConstantThrottlingStrategy(Ticker ticker,
                               ConcurrentMap<String, AtomicLong> partitions,
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
        return newResultPromise(throttleSync(partitionKey, throttlingRate));
    }

    private long throttleSync(String partitionKey, ThrottlingRate throttlingRate) {
        final long nanosToWaitForNextToken = throttlingRate.delayBetweenRequests(NANOSECONDS);
        for (;;) {
            long now = ticker.read();
            AtomicLong currentExpireToken = partitions.get(partitionKey);
            if (currentExpireToken == null) {
                // it seems we are the first one for that partition
                AtomicLong newExpireToken = partitions.putIfAbsent(partitionKey,
                                                                   new AtomicLong(now + nanosToWaitForNextToken));
                if (newExpireToken == null) {
                    // We were the first thread to take that slot, return immediately
                    return 0L;
                }
                currentExpireToken = newExpireToken;
            }
            long currentExpirationTimestamp = currentExpireToken.get();
            if (!isExpired(currentExpirationTimestamp, now)) {
                return Math.max(MILLISECONDS.convert(currentExpirationTimestamp - now, NANOSECONDS), 1);
            }

            // Enough time is elapsed since the previous token, let's try to continue with it.
            if (currentExpireToken.compareAndSet(currentExpirationTimestamp, now + nanosToWaitForNextToken)) {
                return 0L;
            }
            // The token was already changed by another thread. Let's try again.
            // There is high chance that's the token is not expired, but then we can return an accurate value.
        }
    }

    private static boolean isExpired(long previous, long current) {
        return previous <= current;
    }

    @Override
    public void stop() {
        cleaningFuture.cancel(false);
        partitions.clear();
    }

}
