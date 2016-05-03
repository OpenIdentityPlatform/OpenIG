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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConstantThrottlingStrategyTest {

    private static final ThrottlingRate FIVE_PER_SEC = new ThrottlingRate(5, duration(1, SECONDS));
    private static final ThrottlingRate SIX_PER_SEC = new ThrottlingRate(6, duration(1, SECONDS));
    private static final String FOO = "foo";
    private static final String BAR = "bar";

    private static final Duration CLEANING_INTERVAL = Duration.duration("5 seconds");

    ConstantThrottlingStrategy strategy;
    FakeTicker ticker;

    @BeforeMethod
    public void beforeMethod() {
        ticker = new FakeTicker();
        ScheduledExecutorService scheduledExecutor = mock(ScheduledExecutorService.class);
        when(scheduledExecutor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));

        strategy = new ConstantThrottlingStrategy(ticker, scheduledExecutor, CLEANING_INTERVAL);
    }

    @AfterMethod
    public void afterMethod() {
        strategy.stop();
    }

    @Test
    public void shouldOutputAtConstantRate() throws Exception {
        long delay;
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);

        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertRejected(delay);

        // No request can be accepted before 200 ms ( 1 sec / 5 )
        ticker.advance(200, MILLISECONDS);
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);
    }

    @Test
    public void shouldAdaptTheRateToTheNewThrottlingRate() throws Exception {
        long delay;
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);

        // The new rate can't be taken into account now, as the previous one is not yet expired
        ticker.advance(166, MILLISECONDS); // (1 sec / 6)
        delay = strategy.throttle(FOO, SIX_PER_SEC).get();
        assertRejected(delay);

        ticker.advance(34, MILLISECONDS); // t + 200ms
        delay = strategy.throttle(FOO, SIX_PER_SEC).get();
        assertAccepted(delay);

        // Now the new rate is taken into account
        ticker.advance(167, MILLISECONDS); // (1 sec / 6 rounded up)
        delay = strategy.throttle(FOO, SIX_PER_SEC).get();
        assertAccepted(delay);
    }

    @Test
    public void shouldIsolateThePartitions() throws Exception {
        long delay;
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);
        delay = strategy.throttle(BAR, SIX_PER_SEC).get();
        assertAccepted(delay);

        // Only the partition "bar" can accept a request after 170 ms (a bit more than 1/6)
        ticker.advance(170, MILLISECONDS);
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertRejected(delay);
        delay = strategy.throttle(BAR, SIX_PER_SEC).get();
        assertAccepted(delay);

        // Both partitions can accept some requests after 470 ms (a bit more than 2*1/6)
        ticker.advance(300, MILLISECONDS);
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);
        delay = strategy.throttle(BAR, SIX_PER_SEC).get();
        assertAccepted(delay);
    }

    @Test
    public void shouldReturnTheDelayToWaitForTheNextAcceptedTry() throws Exception {
        long delay;
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);

        // The same request will be refused as the previous token is not yet expired
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertThat(delay).isEqualTo(200);

        // Even if ticker was forwarded by 50ms it's not enough for the token to be expired but the returned delay is
        // returning the correct value to wait for the next valid try
        ticker.advance(50, MILLISECONDS);
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertThat(delay).isEqualTo(150);

        // Let's follow the advice and retry in the given delay
        ticker.advance(delay, MILLISECONDS);
        delay = strategy.throttle(FOO, FIVE_PER_SEC).get();
        assertAccepted(delay);
    }

    private static void assertRejected(long delay) {
        assertThat(delay).isGreaterThan(0);
    }

    private static void assertAccepted(long delay) {
        assertThat(delay).isLessThanOrEqualTo(0);
    }

}
