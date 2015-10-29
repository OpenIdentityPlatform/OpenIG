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

import java.util.Map;

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
import com.codahale.metrics.MetricRegistry;

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
        MetricRegistry registry = new MetricRegistry();

        Filter filter = new MetricsFilter(registry);

        filter.filter(null, new Request(), next);

        Map<String, Counter> requests = getGroupCounters(registry, "request-counts");
        assertThat(requests.get("all").getCount()).isEqualTo(1);
        assertThat(requests.get("inflight").getCount()).isEqualTo(0);

        Map<String, Counter> responses = getGroupCounters(registry, "response-counts");
        assertThat(responses.get("all").getCount()).isEqualTo(1);
        assertThat(responses.get("with-errors").getCount()).isEqualTo(0);
        assertThat(responses.get("other-families").getCount()).isEqualTo(0);

        assertThat(responses.get("1xx").getCount()).isEqualTo(0);
        assertThat(responses.get("2xx").getCount()).isEqualTo(1);
        assertThat(responses.get("3xx").getCount()).isEqualTo(0);
        assertThat(responses.get("4xx").getCount()).isEqualTo(0);
        assertThat(responses.get("5xx").getCount()).isEqualTo(0);

        assertThat(registry.timer("response-rates").getMeanRate()).isNotEqualTo(0);
        assertThat(registry.counter("total-processing-time").getCount()).isNotEqualTo(0);
    }

    @Test
    public void shouldShowInflightsRequest() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        final PromiseImpl<Response, NeverThrowsException> promise = Response.newResponsePromiseImpl();
        Handler next = new Handler() {
            @Override
            public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
                return promise;
            }
        };
        Filter filter = new MetricsFilter(registry);

        Counter counter = getGroupCounters(registry, "request-counts").get("inflight");
        assertThat(counter.getCount()).isEqualTo(0);

        filter.filter(null, new Request(), next);

        assertThat(counter.getCount()).isEqualTo(1);

        promise.handleResult(new Response(Status.OK));
        assertThat(counter.getCount()).isEqualTo(0);
    }

    private Map<String, Counter> getGroupCounters(MetricRegistry registry, String groupName) {
        return ((MetricsFilter.CounterGroup) registry.getMetrics().get(groupName)).getCounters();
    }

}
