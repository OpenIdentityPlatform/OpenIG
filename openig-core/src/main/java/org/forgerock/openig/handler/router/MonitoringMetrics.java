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

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;

/**
 * Holds the set of metrics needed for monitoring.
 */
class MonitoringMetrics {
    private final Counter totalResponseCount;
    private final Counter informativeResponseCount;
    private final Counter successResponseCount;
    private final Counter redirectResponseCount;
    private final Counter clientErrorResponseCount;
    private final Counter serverErrorResponseCount;
    private final Counter otherResponseCount;
    private final Counter errorsResponseCount;
    private final Counter totalRequestCount;
    private final Counter activeRequestCount;
    private final Meter throughput;
    private final Histogram responseTime;
    private final Counter accumulatedResponseTime;

    public MonitoringMetrics() {
        this.totalResponseCount = new Counter();
        this.informativeResponseCount = new Counter();
        this.successResponseCount = new Counter();
        this.redirectResponseCount = new Counter();
        this.clientErrorResponseCount = new Counter();
        this.serverErrorResponseCount = new Counter();
        this.otherResponseCount = new Counter();
        this.errorsResponseCount = new Counter();

        this.totalRequestCount = new Counter();
        this.activeRequestCount = new Counter();

        this.throughput = new Meter();
        this.responseTime = new Histogram(new ExponentiallyDecayingReservoir());
        this.accumulatedResponseTime = new Counter();
    }

    public Counter getTotalResponseCount() {
        return totalResponseCount;
    }

    public Counter getInformativeResponseCount() {
        return informativeResponseCount;
    }

    public Counter getSuccessResponseCount() {
        return successResponseCount;
    }

    public Counter getRedirectResponseCount() {
        return redirectResponseCount;
    }

    public Counter getClientErrorResponseCount() {
        return clientErrorResponseCount;
    }

    public Counter getServerErrorResponseCount() {
        return serverErrorResponseCount;
    }

    public Counter getOtherResponseCount() {
        return otherResponseCount;
    }

    public Counter getErrorsResponseCount() {
        return errorsResponseCount;
    }

    public Counter getTotalRequestCount() {
        return totalRequestCount;
    }

    public Counter getActiveRequestCount() {
        return activeRequestCount;
    }

    public Meter getThroughput() {
        return throughput;
    }

    public Histogram getResponseTime() {
        return responseTime;
    }

    public Counter getAccumulatedResponseTime() {
        return accumulatedResponseTime;
    }
}
