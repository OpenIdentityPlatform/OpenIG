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

import java.util.concurrent.TimeUnit;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;

import com.codahale.metrics.MetricRegistry;

/**
 * Collect request processing metrics.
 */
class MetricsFilter implements Filter {

    private final MonitoringMetrics metrics;

    /**
     * Constructs a MetricsFilter, filling the provided {@linkplain MetricRegistry registry} with metrics.
     *
     * @param metrics
     *         monitoring metrics registry
     */
    MetricsFilter(final MonitoringMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        metrics.getTotalRequestCount().inc();
        metrics.getActiveRequestCount().inc();
        final long start = System.nanoTime();

        return next.handle(context, request)
                       .thenOnResult(new ResultHandler<Response>() {
                           @Override
                           public void handleResult(final Response result) {
                               // Elapsed time is computed in microseconds
                               long elapsed = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
                               metrics.getAccumulatedResponseTime().inc(elapsed);
                               metrics.getResponseTime().update(elapsed);

                               metrics.getThroughput().mark();

                               metrics.getTotalResponseCount().inc();
                               metrics.getActiveRequestCount().dec();

                               if (result.getCause() != null) {
                                   metrics.getErrorsResponseCount().inc();
                               }
                               Status status = result.getStatus();
                               if (status != null) {
                                   // Response doesn't mandate a Status in constructor :'(
                                   switch (status.getFamily()) {
                                   case INFORMATIONAL:
                                       metrics.getInformativeResponseCount().inc();
                                       break;
                                   case SUCCESSFUL:
                                       metrics.getSuccessResponseCount().inc();
                                       break;
                                   case REDIRECTION:
                                       metrics.getRedirectResponseCount().inc();
                                       break;
                                   case CLIENT_ERROR:
                                       metrics.getClientErrorResponseCount().inc();
                                       break;
                                   case SERVER_ERROR:
                                       metrics.getServerErrorResponseCount().inc();
                                       break;
                                   case UNKNOWN:
                                       metrics.getOtherResponseCount().inc();
                                       break;
                                   }
                               }
                           }
                       });
    }
}
