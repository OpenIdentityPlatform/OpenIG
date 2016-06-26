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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.util.Reject;

/**
 * A TokenBucket is an helper class that aims to limit the output rate. Each ticker the method {@link #tryConsume()} is
 * called, a token is removed from the bucket. Where there is no token anymore into the bucket then the returned value
 * is the ticker to wait for the availability of the token. An attempt to refill the bucket as much as possible, is done
 * prior any token consumption. That is a variant of the Leaky Bucket algorithm, used in network traffic shaping.
 *
 * You may use it like this to limit the number of calls to your API :
 * <pre>
 * {@code
 * Ticker ticker = ...
 * // We want to limit to maximum 10 calls per seconds
 * TokenBucket bucket = new TokenBucket(ticker, 10, Duration.duration("1 seconds"));
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
 *     TimeUnit.NANOSECONDS.sleep(delay);
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

    private final Ticker ticker;
    private final ThrottlingRate throttlingRate;
    private final int capacity;
    private final long duration; // in nanoseconds
    private final AtomicReference<State> state;
    private final long nanosToWaitForNextToken;

    /**
     * Construct a TokenBucket.
     *
     * @param ticker
     *            the ticker service to use.
     * @param rate
     *            the rate applied on this bucket.
     */
    public TokenBucket(Ticker ticker, ThrottlingRate rate) {
        Reject.ifNull(ticker);
        this.ticker = ticker;
        this.throttlingRate = rate;
        this.capacity = rate.getNumberOfRequests();
        this.duration = rate.getDuration().to(NANOSECONDS);
        this.nanosToWaitForNextToken = rate.delayBetweenRequests(NANOSECONDS);
        this.state = new AtomicReference<>();
    }

    /**
     * Consume a token from the bucket. The returned delay is just an indication, and event if wait for that delay,
     * there is no guarantee that this next call will succeed.
     *
     * @return the delay to wait before a next token can be consumed. If it is equal to 0, that means a
     * token has been consumed from the bucket, if it is greater than 0, that means the delay to wait, in
     * nanoseconds, for having an opportunity to consume a token.
     */
    public long tryConsume() {
        do {
            final long now = ticker.read();

            final State currentState = state.get();
            final State newState;
            if (currentState == null) {
                // First ticker, start at full capacity minus the current call.
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
                    long delayForNextRetry = (currentState.timestampLastRefill + this.nanosToWaitForNextToken) - now;
                    // Return at least 1ns to indicate we did not consume a token
                    return Math.max(1, delayForNextRetry);
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
        return elapsedTime / this.nanosToWaitForNextToken;
    }

    long getRemainingTokensCount() {
        State currentState = state.get();
        return currentState == null ? capacity : currentState.counter;
    }

    public ThrottlingRate getThrottlingRate() {
        return throttlingRate;
    }

    private long getTimestampLastRefill() {
        State currentState = state.get();
        return currentState.timestampLastRefill;
    }

    /**
     * Returns whether this token bucket is expired or not, meaning that the difference between now and the last refill
     * is greater than the bucket's duration.
     * @return whether this token bucket is expired or not
     */
    public boolean isExpired() {
        return (ticker.read() - getTimestampLastRefill()) > duration;
    }

}
