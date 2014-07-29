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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.forgerock.util.Reject.checkNotNull;

/**
 * Represents a duration in english. Cases is not important, plurals units are accepted.
 *
 * <code>
 *     6 days
 *     59 minutes and 1 millisecond
 *     1 minute and 10 seconds
 *     42 millis
 *     unlimited
 * </code>
 */
public class Duration {

    /**
     * Special duration that represents an unlimited duration (or indefinite).
     */
    private static final Duration UNLIMITED = new Duration(Long.MAX_VALUE, DAYS);

    /**
     * Tokens that represents the unlimited duration.
     */
    private static final Set<String> UNLIMITED_TOKENS = new CaseInsensitiveSet(asList("unlimited",
                                                                                      "indefinite",
                                                                                      "infinity",
                                                                                      "undefined"));

    private Long number;
    private TimeUnit unit;

    /**
     * Builds a new Duration.
     * @param number number of time unit (cannot be {@literal null}).
     * @param unit TimeUnit to express the duration in (cannot be {@literal null}).
     */
    public Duration(final Long number, final TimeUnit unit) {
        this.number = checkNotNull(number);
        this.unit = checkNotNull(unit);
    }

    /**
     * Builds a new {@link Duration} that will represents the given duration expressed in english.
     *
     * @param value
     *         natural speech duration
     * @return a new {@link Duration}
     * @throws IllegalArgumentException
     *         if the input string is incorrectly formatted.
     */
    public static Duration duration(final String value) {
        List<Duration> composite = new ArrayList<Duration>();

        // Split around ',' and ' and ' patterns
        String[] fragments = value.split(",| and ");

        // If there is only 1 fragment and that it matches the recognized "unlimited" tokens
        if ((fragments.length == 1) && UNLIMITED_TOKENS.contains(fragments[0].trim())) {
            // Unlimited Duration
            return UNLIMITED;
        }

        // Build the normal duration
        for (String fragment : fragments) {

            fragment = fragment.trim();

            if ("".equals(fragment)) {
                throw new IllegalArgumentException("Cannot parse empty duration, expecting '<value> <unit>' pattern");
            }

            // Parse the number part
            int i = 0;
            StringBuilder numberSB = new StringBuilder();
            while (Character.isDigit(fragment.charAt(i))) {
                numberSB.append(fragment.charAt(i));
                i++;
            }

            // Ignore whitespace
            while (Character.isWhitespace(fragment.charAt(i))) {
                i++;
            }

            // Parse the time unit part
            StringBuilder unitSB = new StringBuilder();
            while ((i < fragment.length()) && Character.isLetter(fragment.charAt(i))) {
                unitSB.append(fragment.charAt(i));
                i++;
            }
            Long number = Long.valueOf(numberSB.toString());
            TimeUnit unit = parseTimeUnit(unitSB.toString());

            composite.add(new Duration(number, unit));
        }

        // Merge components of the composite together
        Duration duration = new Duration(0L, DAYS);
        for (Duration elements : composite) {
            duration.merge(elements);
        }

        return duration;
    }

    /**
     * Aggregates this Duration with the given Duration. Littlest {@link TimeUnit} will be used as a common ground.
     *
     * @param duration
     *         other Duration
     */
    private void merge(final Duration duration) {
        // find littlest unit
        // conversion will happen on the littlest unit otherwise we loose details
        if (unit.ordinal() > duration.unit.ordinal()) {
            // Other duration is smaller than me
            number = duration.unit.convert(number, unit) + duration.number;
            unit = duration.unit;
        } else {
            // Other duration is greater than me
            number = unit.convert(duration.number, duration.unit) + number;
        }
    }

    /**
     * Parse the given input string as a {@link TimeUnit}.
     */
    private static TimeUnit parseTimeUnit(final String unit) {
        String lowercase = unit.toLowerCase();

        // @Checkstyle:off

        if ("days".equals(lowercase)) return DAYS;
        if ("day".equals(lowercase)) return DAYS;
        if ("d".equals(lowercase)) return DAYS;

        if ("hours".equals(lowercase)) return TimeUnit.HOURS;
        if ("hour".equals(lowercase)) return TimeUnit.HOURS;
        if ("h".equals(lowercase)) return TimeUnit.HOURS;

        if ("minutes".equals(lowercase)) return TimeUnit.MINUTES;
        if ("minute".equals(lowercase)) return TimeUnit.MINUTES;
        if ("min".equals(lowercase)) return TimeUnit.MINUTES;
        if ("m".equals(lowercase)) return TimeUnit.MINUTES;

        if ("seconds".equals(lowercase)) return TimeUnit.SECONDS;
        if ("second".equals(lowercase)) return TimeUnit.SECONDS;
        if ("sec".equals(lowercase)) return TimeUnit.SECONDS;
        if ("s".equals(lowercase)) return TimeUnit.SECONDS;

        if ("milliseconds".equals(lowercase)) return TimeUnit.MILLISECONDS;
        if ("millisecond".equals(lowercase)) return TimeUnit.MILLISECONDS;
        if ("millisec".equals(lowercase)) return TimeUnit.MILLISECONDS;
        if ("millis".equals(lowercase)) return TimeUnit.MILLISECONDS;
        if ("milli".equals(lowercase)) return TimeUnit.MILLISECONDS;
        if ("ms".equals(lowercase)) return TimeUnit.MILLISECONDS;

        if ("microseconds".equals(lowercase)) return TimeUnit.MICROSECONDS;
        if ("microsecond".equals(lowercase)) return TimeUnit.MICROSECONDS;
        if ("microsec".equals(lowercase)) return TimeUnit.MICROSECONDS;
        if ("micros".equals(lowercase)) return TimeUnit.MICROSECONDS;
        if ("micro".equals(lowercase)) return TimeUnit.MICROSECONDS;
        if ("us".equals(lowercase)) return TimeUnit.MICROSECONDS;

        if ("nanoseconds".equals(lowercase)) return TimeUnit.NANOSECONDS;
        if ("nanosecond".equals(lowercase)) return TimeUnit.NANOSECONDS;
        if ("nanosec".equals(lowercase)) return TimeUnit.NANOSECONDS;
        if ("nanos".equals(lowercase)) return TimeUnit.NANOSECONDS;
        if ("nano".equals(lowercase)) return TimeUnit.NANOSECONDS;
        if ("ns".equals(lowercase)) return TimeUnit.NANOSECONDS;

        // @Checkstyle:on

        throw new IllegalArgumentException(format("TimeUnit %s is not recognized", unit));
    }

    /**
     * Returns the number of {@link TimeUnit} this duration represents.
     *
     * @return the number of {@link TimeUnit} this duration represents.
     */
    public long getValue() {
        return number;
    }

    /**
     * Returns the {@link TimeUnit} this duration is expressed in.
     *
     * @return the {@link TimeUnit} this duration is expressed in.
     */
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * Convert the current duration to a given {@link TimeUnit}.
     * Conversions from finer to coarser granularities truncate, so lose precision.
     *
     * @param targetUnit
     *         target unit of the conversion.
     * @return converted duration
     * @see TimeUnit#convert(long, TimeUnit)
     */
    public Duration convertTo(TimeUnit targetUnit) {
        return new Duration(targetUnit.convert(number, unit), targetUnit);
    }

    /**
     * Convert the current duration to a number of given {@link TimeUnit}.
     * Conversions from finer to coarser granularities truncate, so lose precision.
     *
     * @param targetUnit
     *         target unit of the conversion.
     * @return converted duration value
     * @see TimeUnit#convert(long, TimeUnit)
     */
    public long to(TimeUnit targetUnit) {
        return convertTo(targetUnit).getValue();
    }

    /**
     * Returns {@literal true} if this Duration represents at unlimited duration.
     *
     * @return {@literal true} if this Duration represents at unlimited duration.
     */
    public boolean isUnlimited() {
        return this == UNLIMITED;
    }

}
