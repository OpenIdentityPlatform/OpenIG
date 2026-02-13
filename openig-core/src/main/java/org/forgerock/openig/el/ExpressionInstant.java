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

package org.forgerock.openig.el;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * {@link java.time.Instant} wrapper to use in OpenIG expression language
 *
 * @see org.forgerock.openig.el.plugins.ExpressionInstantPlugin
 *
 */

public class ExpressionInstant {

    Instant instant;

    public ExpressionInstant(Instant instant) {
        this.instant = instant;
    }
    public long getEpochMillis() {
        return this.instant.toEpochMilli();
    }

    public long getEpochSeconds() {
        return this.instant.getEpochSecond();
    }

    public ExpressionInstant minusDays(long daysToSubtract) {
        return new ExpressionInstant(this.instant.minus(daysToSubtract, ChronoUnit.DAYS));
    }


    public ExpressionInstant minusHours(long hoursToSubtract) {
        return new ExpressionInstant(this.instant.minus(hoursToSubtract, ChronoUnit.HOURS));
    }

    public ExpressionInstant minusMillis(long millisecondsToSubtract) {
        return new ExpressionInstant(this.instant.minusMillis(millisecondsToSubtract));
    }

    public ExpressionInstant minusMinutes(long minutesToSubtract) {
        return new ExpressionInstant(this.instant.minus(minutesToSubtract, ChronoUnit.MINUTES));
    }

    public ExpressionInstant minusSeconds(long secondsToSubtract) {
        return new ExpressionInstant(this.instant.minus(secondsToSubtract, ChronoUnit.SECONDS));
    }

    public ExpressionInstant plusDays(long daysToAdd) {
        return new ExpressionInstant(this.instant.plus(daysToAdd, ChronoUnit.DAYS));
    }

    public ExpressionInstant plusHours(long hoursToAdd) {
        return new ExpressionInstant(this.instant.plus(hoursToAdd, ChronoUnit.DAYS));
    }

    public ExpressionInstant plusMillis(long millisecondsToAdd) {
        return new ExpressionInstant(this.instant.plus(millisecondsToAdd, ChronoUnit.DAYS));
    }

    public ExpressionInstant plusMinutes(long minutesToAdd) {
        return new ExpressionInstant(this.instant.plus(minutesToAdd, ChronoUnit.DAYS));
    }

    public ExpressionInstant plusSeconds(long secondsToAdd) {
        return new ExpressionInstant(this.instant.plus(secondsToAdd, ChronoUnit.DAYS));
    }
}
