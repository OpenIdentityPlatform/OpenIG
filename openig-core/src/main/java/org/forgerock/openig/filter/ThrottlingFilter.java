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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.util.time.Duration.duration;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Keys;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * This filter allows to limit the output rate to the specified handler. If the output rate is over, there a response
 * with status 429 (Too Many Requests) is sent.
 */
public class ThrottlingFilter extends GenericHeapObject implements Filter {

    private final TokenBucket bucket;

    /**
     * Constructs a ThrottlingFilter.
     *
     * @param time the time service.
     * @param numberOfRequests the maximum of requests that can be filtered out during the duration.
     * @param duration the duration of the sliding window
     */
    public ThrottlingFilter(TimeService time, int numberOfRequests, Duration duration) {
        this.bucket = new TokenBucket(time, numberOfRequests, duration);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final long delay = bucket.tryConsume();
        if (delay <= 0) {
            return next.handle(context, request);
        } else {
            // http://tools.ietf.org/html/rfc6585#section-4
            Response result = new Response(Status.TOO_MANY_REQUESTS);
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
            result.getHeaders().add("Retry-After", Long.toString(SECONDS.convert(delay, MILLISECONDS)));
            return Promises.newResultPromise(result);
        }
    }

    /**
     * Creates and initializes a throttling filter in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            TimeService time = heap.get(Keys.TIME_SERVICE_HEAP_KEY, TimeService.class);

            JsonValue rate = config.get("rate").required();

            Integer numberOfRequests = rate.get("numberOfRequests").required().asInteger();
            Duration duration = duration(rate.get("duration").required().asString());

            return new ThrottlingFilter(time, numberOfRequests, duration);
        }
    }
}
