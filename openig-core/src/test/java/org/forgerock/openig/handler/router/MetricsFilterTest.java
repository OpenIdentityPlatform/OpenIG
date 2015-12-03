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

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.filter.ResponseHandler;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.codahale.metrics.Counter;

@SuppressWarnings("javadoc")
public class MetricsFilterTest {

    private ResponseHandler next;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        next = new ResponseHandler(new Response(Status.OK));
    }

    @Test
    public void shouldUpdateMetrics() throws Exception {
        MonitoringMetrics metrics = new MonitoringMetrics();

        Filter filter = new MetricsFilter(metrics);

        filter.filter(null, new Request(), next);

        assertThat(metrics.getTotalRequestCount().getCount()).isEqualTo(1);
        assertThat(metrics.getActiveRequestCount().getCount()).isEqualTo(0);

        assertThat(metrics.getTotalResponseCount().getCount()).isEqualTo(1);
        assertThat(metrics.getErrorsResponseCount().getCount()).isEqualTo(0);
        assertThat(metrics.getOtherResponseCount().getCount()).isEqualTo(0);

        assertThat(metrics.getInformativeResponseCount().getCount()).isEqualTo(0);
        assertThat(metrics.getSuccessResponseCount().getCount()).isEqualTo(1);
        assertThat(metrics.getRedirectResponseCount().getCount()).isEqualTo(0);
        assertThat(metrics.getClientErrorResponseCount().getCount()).isEqualTo(0);
        assertThat(metrics.getServerErrorResponseCount().getCount()).isEqualTo(0);

        assertThat(metrics.getThroughput().getMeanRate()).isNotEqualTo(0);
        assertThat(metrics.getResponseTime().getCount()).isEqualTo(1);
        assertThat(metrics.getAccumulatedResponseTime().getCount()).isNotEqualTo(0);
    }

    @Test
    public void shouldShowOneActiveRequest() throws Exception {
        final PromiseImpl<Response, NeverThrowsException> promise = Response.newResponsePromiseImpl();
        Handler next = new Handler() {
            @Override
            public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
                return promise;
            }
        };
        MonitoringMetrics metrics = new MonitoringMetrics();

        Filter filter = new MetricsFilter(metrics);

        Counter counter = metrics.getActiveRequestCount();
        assertThat(counter.getCount()).isEqualTo(0);

        filter.filter(null, new Request(), next);

        assertThat(counter.getCount()).isEqualTo(1);

        promise.handleResult(new Response(Status.OK));
        assertThat(counter.getCount()).isEqualTo(0);
    }

}
