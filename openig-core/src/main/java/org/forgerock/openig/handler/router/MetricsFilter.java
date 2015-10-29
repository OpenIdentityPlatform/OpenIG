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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

/**
 * Collect request processing metrics.
 */
class MetricsFilter implements Filter {

    private final Counter requestCount;
    private final Counter responseCount;
    private final Counter informativeResponseCount;
    private final Counter successResponseCount;
    private final Counter redirectResponseCount;
    private final Counter clientErrorResponseCount;
    private final Counter serverErrorResponseCount;
    private final Counter otherResponseCount;
    private final Counter errorsResponseCount;
    private final Timer responseRates;
    private final Counter totalProcessingTime;
    private final Counter inflightRequestCount;

    /**
     * Constructs a MetricsFilter, filling the provided {@linkplain MetricRegistry registry} with metrics.
     *
     * @param registry
     *         metrics registry
     */
    MetricsFilter(final MetricRegistry registry) {
        CounterGroup responses = registry.register("response-counts", new CounterGroup());
        this.responseCount = responses.counter("all");
        this.informativeResponseCount = responses.counter("1xx");
        this.successResponseCount = responses.counter("2xx");
        this.redirectResponseCount = responses.counter("3xx");
        this.clientErrorResponseCount = responses.counter("4xx");
        this.serverErrorResponseCount = responses.counter("5xx");
        this.otherResponseCount = responses.counter("other-families");
        this.errorsResponseCount = responses.counter("with-errors");

        CounterGroup requests = registry.register("request-counts", new CounterGroup());
        this.requestCount = requests.counter("all");
        this.inflightRequestCount = requests.counter("inflight");

        this.responseRates = registry.timer("response-rates");
        this.totalProcessingTime = registry.counter("total-processing-time");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        requestCount.inc();
        inflightRequestCount.inc();
        final Timer.Context time = responseRates.time();

        return next.handle(context, request)
                       .thenOnResult(new ResultHandler<Response>() {
                           @Override
                           public void handleResult(final Response result) {
                               long elapsed = time.stop();
                               totalProcessingTime.inc(elapsed);

                               responseCount.inc();
                               inflightRequestCount.dec();

                               if (result.getCause() != null) {
                                   errorsResponseCount.inc();
                               }
                               Status status = result.getStatus();
                               if (status != null) {
                                   // Response doesn't mandate a Status in constructor :'(
                                   switch (status.getFamily()) {
                                   case INFORMATIONAL:
                                       informativeResponseCount.inc();
                                       break;
                                   case SUCCESSFUL:
                                       successResponseCount.inc();
                                       break;
                                   case REDIRECTION:
                                       redirectResponseCount.inc();
                                       break;
                                   case CLIENT_ERROR:
                                       clientErrorResponseCount.inc();
                                       break;
                                   case SERVER_ERROR:
                                       serverErrorResponseCount.inc();
                                       break;
                                   case UNKNOWN:
                                       otherResponseCount.inc();
                                       break;
                                   }
                               }
                           }
                       });
    }

    /**
     * Group Counters together, this is mainly used for later structured representation of the results.
     */
    static class CounterGroup implements Metric {
        private final Map<String, Counter> counters = new LinkedHashMap<>();

        public Counter counter(String name) {
            Counter counter = new Counter();
            counters.put(name, counter);
            return counter;
        }

        public Map<String, Counter> getCounters() {
            return counters;
        }
    }
}
