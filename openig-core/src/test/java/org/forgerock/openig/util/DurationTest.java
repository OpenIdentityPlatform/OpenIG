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

import org.assertj.core.api.ObjectAssert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DurationTest {

    @Test
    public void testOneMinute() throws Exception {
        assertThat(new Duration("1 minute")).isEqualTo(1L, TimeUnit.MINUTES);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds() throws Exception {
        assertThat(new Duration("2 minutes and 20 seconds"))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds2() throws Exception {
        assertThat(new Duration("2 minutes, 20 seconds"))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testTwoMinutesAndTwentySeconds3() throws Exception {
        assertThat(new Duration("   2     minutes   and    20   seconds   "))
                .isEqualTo(140L, TimeUnit.SECONDS);
    }

    @Test
    public void testThreeDays() throws Exception {
        assertThat(new Duration("3 days"))
                .isEqualTo(3L, TimeUnit.DAYS);
    }

    @Test
    public void testCompact() throws Exception {
        assertThat(new Duration("3d,2h,1m"))
                .isEqualTo(4441L, TimeUnit.MINUTES);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput() throws Exception {
        new Duration(" 3 3 minutes");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput2() throws Exception {
        new Duration("minutes");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidInput3() throws Exception {
        new Duration("   ");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnrecognizedTimeUnit() throws Exception {
        new Duration("3 blah");
    }

    @Test
    public void testKnownTimeUnits() throws Exception {

        assertThat(new Duration("1 days")).isEqualTo(1L, TimeUnit.DAYS);
        assertThat(new Duration("1 day")).isEqualTo(1L, TimeUnit.DAYS);
        assertThat(new Duration("1 d")).isEqualTo(1L, TimeUnit.DAYS);

        assertThat(new Duration("1 hours")).isEqualTo(1L, TimeUnit.HOURS);
        assertThat(new Duration("1 hour")).isEqualTo(1L, TimeUnit.HOURS);
        assertThat(new Duration("1 h")).isEqualTo(1L, TimeUnit.HOURS);

        assertThat(new Duration("1 minutes")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(new Duration("1 minute")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(new Duration("1 min")).isEqualTo(1L, TimeUnit.MINUTES);
        assertThat(new Duration("1 m")).isEqualTo(1L, TimeUnit.MINUTES);

        assertThat(new Duration("1 seconds")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(new Duration("1 second")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(new Duration("1 sec")).isEqualTo(1L, TimeUnit.SECONDS);
        assertThat(new Duration("1 s")).isEqualTo(1L, TimeUnit.SECONDS);

        assertThat(new Duration("1 milliseconds")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(new Duration("1 millisecond")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(new Duration("1 millisec")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(new Duration("1 millis")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(new Duration("1 milli")).isEqualTo(1L, TimeUnit.MILLISECONDS);
        assertThat(new Duration("1 ms")).isEqualTo(1L, TimeUnit.MILLISECONDS);

        assertThat(new Duration("1 microseconds")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(new Duration("1 microsecond")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(new Duration("1 microsec")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(new Duration("1 micros")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(new Duration("1 micro")).isEqualTo(1L, TimeUnit.MICROSECONDS);
        assertThat(new Duration("1 us")).isEqualTo(1L, TimeUnit.MICROSECONDS);

        assertThat(new Duration("1 nanoseconds")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(new Duration("1 nanosecond")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(new Duration("1 nanosec")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(new Duration("1 nanos")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(new Duration("1 nano")).isEqualTo(1L, TimeUnit.NANOSECONDS);
        assertThat(new Duration("1 ns")).isEqualTo(1L, TimeUnit.NANOSECONDS);
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

    }
}
