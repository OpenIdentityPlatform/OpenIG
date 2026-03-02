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

import com.google.common.testing.FakeTicker;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;

public class TokenRateLimiterTest {

    private static final String USER_A = "alice";
    private static final String USER_B = "bob";

    @Test
    public void shouldReturnZeroWhenAllowed() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(10_000, duration("1 minute"));
        long result = limiter.tryConsume(USER_A, 500);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    public void shouldReturnPositiveNanosWhenDenied() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(100, duration("1 minute"));
        // Cost exceeds full capacity
        long result = limiter.tryConsume(USER_A, 200);
        assertThat(result).isGreaterThan(0L);
    }

    @Test
    public void shouldReturnZeroForExactCapacityCost() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(1_000, duration("1 minute"));
        long result = limiter.tryConsume(USER_A, 1_000);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    public void shouldDenySubsequentRequestWhenBucketExhausted() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(1_000, duration("1 minute"));
        limiter.tryConsume(USER_A, 900); // 100 left
        long result = limiter.tryConsume(USER_A, 200); // needs 200
        assertThat(result).isGreaterThan(0L);
    }

    @Test
    public void shouldTrackBucketsPerIdentityIndependently() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(500, duration("1 minute"));

        assertThat(limiter.tryConsume(USER_A, 500)).isEqualTo(0L);
        assertThat(limiter.tryConsume(USER_A, 1)).isGreaterThan(0L);
        // USER_B has a full bucket
        assertThat(limiter.tryConsume(USER_B, 500)).isEqualTo(0L);
    }

    @Test
    public void shouldRefillTokensAfterElapsedTime() throws Exception {
        FakeTicker ticker = new FakeTicker();

        TokenRateLimiter limiter = new TokenRateLimiter(1_000, duration("1 minute"), ticker::read, null);
        limiter.tryConsume(USER_A, 970);
        ticker.advance(3, TimeUnit.SECONDS);
        long result = limiter.tryConsume(USER_A, 60);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    public void shouldNotExceedCapacityDuringRefill() {
        FakeTicker ticker = new FakeTicker();
        TokenRateLimiter limiter = new TokenRateLimiter(1_000, duration("1 second"), ticker::read, null);

        ticker.advance(10, TimeUnit.MINUTES);

        assertThat(limiter.availableTokens(USER_A)).isEqualTo(1_000);
    }

    @Test
    public void shouldHandleConcurrentConsumersWithoutExceedingCapacity() throws InterruptedException {
        final int THREADS = 100;
        final long CAPACITY = 1_000;
        TokenRateLimiter limiter = new TokenRateLimiter(CAPACITY, duration("1 minute"));

        AtomicInteger allowed = new AtomicInteger(0);
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(() -> {
                try {
                    if (limiter.tryConsume(USER_A, 100) == 0L) {
                        allowed.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertThat(allowed.get()).isLessThanOrEqualTo((int) (CAPACITY / 100));
    }


}
