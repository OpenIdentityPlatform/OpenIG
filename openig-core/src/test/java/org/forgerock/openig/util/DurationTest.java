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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static org.forgerock.openig.util.Duration.duration;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("javadoc")
public class DurationTest {

    @Test
    public void testOneMinute() throws Exception {
        assertThat(duration("1 minute")).isEqualTo(1L, TimeUnit.MINUTES);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds() throws Exception {
        assertThat(duration("2 minutes and 20 seconds"))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds2() throws Exception {
        assertThat(duration("2 minutes, 20 seconds"))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds3() throws Exception {
        assertThat(duration("   2     minutes   and    20   seconds   "))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testThreeDays() throws Exception {
        assertThat(duration("3 days"))
                .isEqualTo(3L, TimeUnit.DAYS);
    }

    @Test
    public void testCompact() throws Exception {
        assertThat(duration("3d,2h,1m"))
                .isEqualTo(4441L, TimeUnit.MINUTES);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput() throws Exception {
        duration(" 3 3 minutes");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput2() throws Exception {
        duration("minutes");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput3() throws Exception {
        duration("   ");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnrecognizedTimeUnit() throws Exception {
        duration("3 blah");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMisUsedUnlimitedKeyword() throws Exception {
        duration("unlimited and 3 minutes");
    }

    @Test
    public void testKnownTimeUnits() throws Exception {

        assertThat(duration("1 days")).isEqualTo(1L, TimeUnit.DAYS);
        assertThat(duration("1 day")).isEqualTo(1L, TimeUnit.DAYS);
        assertThat(duration("1 d")).isEqualTo(1L, TimeUnit.DAYS);

        assertThat(duration("1 hours")).isEqualTo(1L, TimeUnit.HOURS);
        assertThat(duration("1 hour")).isEqualTo(1L, TimeUnit.HOURS);
        assertThat(duration("1 h")).isEqualTo(1L, TimeUnit.HOURS);

        assertThat(duration("1 minutes")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(duration("1 minute")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(duration("1 min")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(duration("1 m")).isEqualTo(1L, TimeUnit.MINUTES);

        assertThat(duration("1 seconds")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(duration("1 second")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(duration("1 sec")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(duration("1 s")).isEqualTo(1L, TimeUnit.SECONDS);

        assertThat(duration("1 milliseconds")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(duration("1 millisecond")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(duration("1 millisec")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(duration("1 millis")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(duration("1 milli")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(duration("1 ms")).isEqualTo(1L, TimeUnit.MILLISECONDS);

        assertThat(duration("1 microseconds")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(duration("1 microsecond")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(duration("1 microsec")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(duration("1 micros")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(duration("1 micro")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(duration("1 us")).isEqualTo(1L, TimeUnit.MICROSECONDS);

        assertThat(duration("1 nanoseconds")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(duration("1 nanosecond")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(duration("1 nanosec")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(duration("1 nanos")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(duration("1 nano")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(duration("1 ns")).isEqualTo(1L, TimeUnit.NANOSECONDS);
    }

    @Test
    public void shouldAcceptZero() throws Exception {
        assertThat(duration("0 ns")).isEqualTo(0L, TimeUnit.NANOSECONDS);
    }

    @Test
    public void shouldSupportUnlimitedDuration() throws Exception {
        assertThat(duration("unlimited")).isUnlimited();
        assertThat(duration("indefinite")).isUnlimited();
        assertThat(duration("infinity")).isUnlimited();
        assertThat(duration("undefined")).isUnlimited();
    }

    @Test
    public void shouldConvertValue() throws Exception {
        assertThat(duration("1 hour").convertTo(TimeUnit.SECONDS))
                .isEqualTo(3600L, TimeUnit.SECONDS);
        Assertions.assertThat(duration("1 hour").to(TimeUnit.SECONDS))
                  .isEqualTo(3600L);
    }

    public DurationAssert assertThat(Duration duration) {
        return new DurationAssert(duration);
    }

    /**
     * Provide a better assertion method.
     */
    private static class DurationAssert extends ObjectAssert<Duration> {

        protected DurationAssert(final Duration actual) {
            super(actual);
        }

        public DurationAssert isEqualTo(long n, TimeUnit unit) {
            isNotNull();
            if (actual.getValue() != n) {
                failWithMessage("Duration value does not match: was:%d expected:%d", actual.getValue(), n);
            }
            if (!actual.getUnit().equals(unit)) {
                failWithMessage("Duration TimeUnit does not match: was:%s expected:%s",
                                actual.getUnit().name(),
                                unit.name());
            }
            return this;
        }

        public DurationAssert isUnlimited() {
            isNotNull();
            if (!actual.isUnlimited()) {
                failWithMessage("Duration is not unlimited current values: %d %s", actual.getValue(), actual.getUnit());
            }
            return this;
        }

    }
}
