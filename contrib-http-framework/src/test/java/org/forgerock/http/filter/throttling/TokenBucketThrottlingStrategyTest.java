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

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.filter.throttling.ThrottlingAssertions.assertAccepted;
import static org.forgerock.http.filter.throttling.ThrottlingAssertions.assertRejected;
import static org.forgerock.util.time.Duration.UNLIMITED;
import static org.forgerock.util.time.Duration.ZERO;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Ticker;
import org.forgerock.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TokenBucketThrottlingStrategyTest {

    private static final ThrottlingRate THROTTLING_RATE_5_PER_SEC = new ThrottlingRate(5, duration(1, SECONDS));
    private static final ThrottlingRate THROTTLING_RATE_6_PER_SEC = new ThrottlingRate(6, duration(1, SECONDS));
    private static final String FOO = "foo";
    private static final String BAR = "bar";

    private static final Duration CLEANING_INTERVAL = Duration.duration("5 seconds");

    TokenBucketThrottlingStrategy strategy;
    FakeTicker ticker;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void beforeMethod() {
        ticker = new FakeTicker();
        ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
        when(scheduledExecutor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        strategy = new TokenBucketThrottlingStrategy(ticker, scheduledExecutor, CLEANING_INTERVAL);
    }

    @AfterMethod
    public void afterMethod() {
        strategy.stop();
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
        new TokenBucketThrottlingStrategy(Ticker.systemTicker(), newSingleThreadScheduledExecutor(), cleaningInterval);
    }

    @Test
    public void shouldUseDifferentBucketsWhenUsingValidPartitionKey() throws Exception {
        // arbitrary variables just to define a valid rate of 1 request per 3 seconds.
        ThrottlingRate throttlingRate = new ThrottlingRate(1, duration("3 seconds"));

        long delay;
        // The first request goes through as the bucket "bar-00" is freshly created
        delay = strategy.throttle("bar-00", throttlingRate).get();
        assertAccepted(delay);

        // The second request has an equivalent throttling definition (same key and same rate), so it uses the same
        // bucket, which is now empty : there is a delay to wait for the next accepted call.
        delay = strategy.throttle("bar-00", throttlingRate).get();
        assertRejected(delay);
        // The third request does *not* have an equivalent throttling definition (the key differs), so it uses another
        // bucket and thus the request can go through the filter.
        delay = strategy.throttle("bar-01", throttlingRate).get();
        assertAccepted(delay);
    }

    @Test
    public void shouldUpdateTheBucketWhenAnotherRateIsSpecified() throws Exception {
        final String partitionKey = "bar-00";
        long delay;
        delay = strategy.throttle(partitionKey, new ThrottlingRate(1, duration("3 seconds"))).get();
        assertAccepted(delay);

        // Call with the same partition key but the rate has changed.
        delay = strategy.throttle(partitionKey, new ThrottlingRate(1, duration("10 seconds"))).get();
        assertAccepted(delay);

        ticker.advance(3, SECONDS);
        delay = strategy.throttle(partitionKey, new ThrottlingRate(1, duration("10 seconds"))).get();
        assertRejected(delay);
    }

    @Test
    public void shouldIsolateThePartitions() throws Exception {
        long delay;

        for (int i = 0; i < 5; i++) {
            delay = strategy.throttle(FOO, THROTTLING_RATE_5_PER_SEC).get();
            assertAccepted(delay);
            delay = strategy.throttle(BAR, THROTTLING_RATE_6_PER_SEC).get();
            assertAccepted(delay);
        }

        // Only the partition "bar" can accept a request after 170 ms (a bit more than 1/6)
        ticker.advance(170, MILLISECONDS);
        delay = strategy.throttle(FOO, THROTTLING_RATE_5_PER_SEC).get();
        assertRejected(delay);
        delay = strategy.throttle(BAR, THROTTLING_RATE_6_PER_SEC).get();
        assertAccepted(delay);

        // Both partitions can accept some requests after 470 ms (a bit more than 2*1/6)
        ticker.advance(300, MILLISECONDS);
        delay = strategy.throttle(FOO, THROTTLING_RATE_5_PER_SEC).get();
        assertAccepted(delay);
        delay = strategy.throttle(BAR, THROTTLING_RATE_6_PER_SEC).get();
        assertAccepted(delay);
    }

    @Test
    public void shouldReturnTheDelayToWaitForTheNextAcceptedTry() throws Exception {
        ThrottlingRate throttlingRate = new ThrottlingRate(1, duration(1, SECONDS));
        long delay;
        delay = strategy.throttle(FOO, throttlingRate).get();
        assertThat(delay).isEqualTo(0);

        // The same request will be refused as the previous token is not yet expired
        delay = strategy.throttle(FOO, throttlingRate).get();
        assertThat(delay).isEqualTo(1_000_000_000);

        // Even if ticker was forwarded by 50ms it's not enough for the token to be expired but the returned delay is
        // returning the correct value to wait for the next valid try
        ticker.advance(50, MILLISECONDS);
        delay = strategy.throttle(FOO, throttlingRate).get();
        assertThat(delay).isEqualTo(950_000_000);

        // Let's follow the advice and retry in the given delay
        ticker.advance(delay, MILLISECONDS);
        delay = strategy.throttle(FOO, throttlingRate).get();
        assertThat(delay).isEqualTo(0);
    }
}
