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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.util.Reject;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A TokenBucket is an helper class that aims to limit the output rate. Each time the method {@link #tryConsume()} is
 * called, a token is removed from the bucket. Where there is no token anymore into the bucket then the returned value
 * is the time to wait for the availability of the token. An attempt to refill the bucket as much as possible, is done
 * prior any token consumption. That is a variant of the Leaky Bucket algorithm, used in network traffic shaping.
 *
 * You may use it like this to limit the number of calls to your API :
 * <pre>
 * {@code
 * TimeService time = ...
 * // We want to limit to maximum 10 calls per seconds
 * TokenBucket bucket = new TokenBucket(time, 10, Duration.duration("1 seconds"));
 *
 * // Then you use it like this :
 * public long doSomething() throws Exception {
 *   long delay = bucket.tryConsume();
 *   if (delay <= 0) {
 *     // You succeeded to consume a token, then you do the job
 *     return fetchCountFromDatabase();
 *   } else {
 *     // You did not succeed to consume a token, then you have 2 choices :
 *     //  - either you give up
 *     //  - or you wait for the delay and try again
 *     // Here we will try again after sleeping a bit :
 *     Thread.sleep(delay);
 *     return doSomething();
 *   }
 * }
 * </pre>
 *
 * @see https://en.wikipedia.org/wiki/Token_bucket
 */
class TokenBucket {

    /**
     * This value class holds the state of the enclosing TokenBucket.
     */
    private static final class State {
        private final long timestampLastRefill;
        private final long counter;

        State(long counter, long timestampLastRefill) {
            this.counter = counter;
            this.timestampLastRefill = timestampLastRefill;
        }
    }

    private final TimeService time;
    private final int capacity;
    private final long duration; // in milliseconds
    private final AtomicReference<State> state;
    private final float millisToWaitForNextToken;

    /**
     * Construct a TokenBucket.
     *
     * @param time
     *            the time service to use.
     * @param capacity
     *            the maximum number of tokens that can take place in the bucket.
     * @param duration
     *            the period of time over which the limit applies.
     */
    public TokenBucket(TimeService time, int capacity, Duration duration) {
        Reject.ifNull(time);
        Reject.ifTrue(capacity <= 0, "The bucket's capacity has to be greater than 0.");
        Reject.ifTrue(duration.isUnlimited(), "The duration can't be unlimited.");
        this.time = time;
        this.capacity = capacity;
        this.duration = duration.to(TimeUnit.MILLISECONDS);
        Reject.ifTrue(this.duration < 1, "The duration has to be greater or equal to 1 ms minimum.");

        this.millisToWaitForNextToken = this.duration / (float) capacity;
        this.state = new AtomicReference<>();
    }

    /**
     * Consume a token from the bucket. The returned delay is just an indication, and event if wait for that delay,
     * there is no guarantee that this next call will succeed.
     *
     * @return the delay to wait before a next token can be consumed. If it is less than or equal to 0, that means a
     *         token has been consumed from the bucket, if it is greater than 0, that means the delay to wait, in
     *         milliseconds, for having an opportunity to consume a token.
     */
    public long tryConsume() {
        do {
            final long now = time.now();

            final State currentState = state.get();
            final State newState;
            if (currentState == null) {
                // First time, start at full capacity minus the current call.
                newState = new State(capacity - 1, now);
            } else {
                long timestampLastRefill = currentState.timestampLastRefill;
                long counter = currentState.counter;
                long newTokens = tokensThatCanBeAdded(now, currentState);
                // Refill the bucket as much as possible
                if (newTokens > 0) {
                    timestampLastRefill = now;
                    counter = Math.min(capacity, currentState.counter + newTokens);
                }

                if (counter <= 0) {
                    // We had not any opportunity to refill the bucket so we just give up
                    return (currentState.timestampLastRefill + (long) this.millisToWaitForNextToken) - now;
                }
                counter--;
                newState = new State(counter, timestampLastRefill);
            }
            if (state.compareAndSet(currentState, newState)) {
                // We succeeded to consume a token and to update the bucket's state
                return 0;
            }
            // Someone else updated the bucket's state before us, let's try again.
        } while (true);
    }

    private long tokensThatCanBeAdded(long now, State state) {
        final long elapsedTime = Math.min(duration, now - state.timestampLastRefill);
        return (long) (elapsedTime / this.millisToWaitForNextToken);
    }

    public long getRemainingTokensCount() {
        State currentState = state.get();
        return currentState == null ? capacity : currentState.counter;
    }

}
