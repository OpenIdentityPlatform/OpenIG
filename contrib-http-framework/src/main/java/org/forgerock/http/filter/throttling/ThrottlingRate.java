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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.Reject;
import org.forgerock.util.time.Duration;

/**
 * A value object to represent a throttling rate.
 */
public final class ThrottlingRate {

    private final int numberOfRequests;
    private final Duration duration;

    /**
     * Constructs a new {@link ThrottlingRate}.
     *
     * @param numberOfRequests the maximum of requests that can be filtered out during the duration.
     * @param duration the duration of the sliding window.
     */
    public ThrottlingRate(int numberOfRequests, Duration duration) {
        Reject.ifTrue(numberOfRequests <= 0, "The bucket's capacity has to be greater than 0.");
        Reject.ifTrue(duration.isUnlimited(), "The duration can't be unlimited.");
        Reject.ifTrue(duration.to(TimeUnit.MILLISECONDS) < 1, "The duration has to be greater or equal to 1 ms "
                + "minimum.");
        this.numberOfRequests = numberOfRequests;
        this.duration = duration;
    }

    /**
     * Returns the maximum of requests that can be filtered out during the duration.
     * @return the maximum of requests that can be filtered out during the duration
     */
    public int getNumberOfRequests() {
        return numberOfRequests;
    }

    /**
     * Returns the duration of the sliding window.
     * @return the duration of the sliding window
     */
    public Duration getDuration() {
        return duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ThrottlingRate that = (ThrottlingRate) o;
        return numberOfRequests == that.numberOfRequests
                && Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfRequests, duration);
    }

    @Override
    public String toString() {
        return numberOfRequests + "/" + duration.getValue() + " " + duration.getUnit();
    }
}
