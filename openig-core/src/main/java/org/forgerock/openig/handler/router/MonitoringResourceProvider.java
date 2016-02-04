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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.SingletonResourceProvider;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Snapshot;

/**
 * Expose monitoring information provided by the given {@link MonitoringMetrics} as a REST resource.
 * This resource only supports read typed operations.
 */
class MonitoringResourceProvider implements SingletonResourceProvider {

    /**
     * Default returned percentiles.
     */
    public static final List<Double> DEFAULT_PERCENTILES = asList(0.999, 0.9999, 0.99999);

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);

    private final MonitoringMetrics metrics;
    private final List<Double> percentiles;

    MonitoringResourceProvider(final MonitoringMetrics metrics) {
        this(metrics, DEFAULT_PERCENTILES);
    }

    MonitoringResourceProvider(final MonitoringMetrics metrics, List<Double> percentiles) {
        this.metrics = metrics;
        this.percentiles = percentiles;
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(final Context context, final ReadRequest request) {
        JsonValue data = json(object());

        // requests
        data.put("requests", object(field("total", metrics.getTotalRequestCount().getCount()),
                                    field("active", metrics.getActiveRequestCount().getCount())));

        // responses
        data.put("responses", object(field("total", metrics.getTotalResponseCount().getCount()),
                                     field("info", metrics.getInformativeResponseCount().getCount()),
                                     field("success", metrics.getSuccessResponseCount().getCount()),
                                     field("redirect", metrics.getRedirectResponseCount().getCount()),
                                     field("clientError", metrics.getClientErrorResponseCount().getCount()),
                                     field("serverError", metrics.getServerErrorResponseCount().getCount()),
                                     field("other", metrics.getOtherResponseCount().getCount()),
                                     field("errors", metrics.getErrorsResponseCount().getCount()),
                                     field("null", metrics.getNullResponseCount().getCount())));

        // throughput (responses / sec) with 1 decimal point (ex: 2511.3 r/s)
        Meter throughput = metrics.getThroughput();
        data.put("throughput", object(field("mean", scale(throughput.getMeanRate())),
                                      field("lastMinute", scale(throughput.getOneMinuteRate())),
                                      field("last5Minutes", scale(throughput.getFiveMinuteRate())),
                                      field("last15Minutes", scale(throughput.getFifteenMinuteRate()))));

        // responseTime (milliseconds), with 3 decimal point (ex: 92.908 ms)
        // total is the accumulated response time: long only
        Snapshot snapshot = metrics.getResponseTime().getSnapshot();
        long accumulatedMillis = MICROSECONDS.toMillis(metrics.getAccumulatedResponseTime().getCount());
        data.put("responseTime", object(field("mean", toMilliseconds(snapshot.getMean())),
                                        field("median", toMilliseconds(snapshot.getMedian())),
                                        field("standardDeviation", toMilliseconds(snapshot.getStdDev())),
                                        field("total", accumulatedMillis),
                                        field("percentiles", percentilesValues(snapshot))));

        return Responses.newResourceResponse(null, null, data).asPromise();
    }

    private Map<String, BigDecimal> percentilesValues(Snapshot snapshot) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Double percentile : percentiles) {
            map.put(String.valueOf(percentile), toMilliseconds(snapshot.getValue(percentile)));
        }
        return map;
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value)
                         .setScale(1, RoundingMode.HALF_DOWN);
    }

    private static BigDecimal toMilliseconds(double value) {
        return BigDecimal.valueOf(value)
                         .divide(ONE_THOUSAND, 3, RoundingMode.HALF_DOWN);
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(final Context context,
                                                                     final ActionRequest request) {
        return new NotSupportedException("Action is not supported by this resource").asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(final Context context,
                                                                      final PatchRequest request) {
        return new NotSupportedException("Patch is not supported by this resource").asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(final Context context,
                                                                       final UpdateRequest request) {
        return new NotSupportedException("Update is not supported by this resource").asPromise();
    }
}
