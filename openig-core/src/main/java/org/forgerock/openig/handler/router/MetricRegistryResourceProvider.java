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

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

/**
 * Expose the provided {@link MetricRegistry} as a REST resource.
 * This resource only supports read typed operations.
 */
class MetricRegistryResourceProvider implements SingletonResourceProvider {

    private final MetricRegistry registry;

    MetricRegistryResourceProvider(final MetricRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(final Context context, final ReadRequest request) {
        return Responses.newResourceResponse(null, null, metric(registry)).asPromise();
    }

    private static JsonValue metric(Metric metric) {
        if (metric instanceof Gauge) {
            return gauge((Gauge) metric);
        }
        if (metric instanceof Counter) {
            return counter((Counter) metric);
        }
        if (metric instanceof Meter) {
            return meter((Meter) metric);
        }
        if (metric instanceof Timer) {
            return timer((Timer) metric);
        }
        if (metric instanceof Histogram) {
            return histogram((Histogram) metric);
        }
        if (metric instanceof MetricSet) {
            return metricSet((MetricSet) metric);
        }
        if (metric instanceof MetricsFilter.CounterGroup) {
            return counterGroup((MetricsFilter.CounterGroup) metric);
        }
        return json("unsupported-metric-type");
    }

    private static JsonValue metricSet(MetricSet metricSet) {
        JsonValue value = new JsonValue(object());
        for (Map.Entry<String, Metric> entry : metricSet.getMetrics().entrySet()) {
            value.put(entry.getKey(), metric(entry.getValue()).getObject());
        }
        return value;
    }

    private static JsonValue counterGroup(MetricsFilter.CounterGroup group) {
        JsonValue value = new JsonValue(object());
        for (Map.Entry<String, Counter> entry : group.getCounters().entrySet()) {
            value.put(entry.getKey(), metric(entry.getValue()).getObject());
        }
        return value;
    }

    private static JsonValue histogram(final Histogram histogram) {
        return json(object(field("count", histogram.getCount()),
                           field("durations", snapshot(histogram.getSnapshot()).getObject())));
    }

    private static JsonValue snapshot(final Snapshot snapshot) {
        return json(object(field("standard-deviation", snapshot.getStdDev()),
                           field("min", snapshot.getMin()),
                           field("max", snapshot.getMax()),
                           field("mean", snapshot.getMean()),
                           field("median", snapshot.getMedian()),
                           field("percentiles", object(field("75th", snapshot.get75thPercentile()),
                                                       field("95th", snapshot.get95thPercentile()),
                                                       field("98th", snapshot.get98thPercentile()),
                                                       field("99th", snapshot.get99thPercentile()),
                                                       field("999th", snapshot.get999thPercentile())))));
    }

    private static JsonValue timer(final Timer timer) {
        return json(object(field("count", timer.getCount()),
                           field("15-minute-rate", timer.getFifteenMinuteRate()),
                           field("5-minute-rate", timer.getFiveMinuteRate()),
                           field("1-minute-rate", timer.getOneMinuteRate()),
                           field("mean-rate", timer.getMeanRate()),
                           field("durations", snapshot(timer.getSnapshot()).getObject())));
    }

    private static JsonValue meter(final Meter meter) {
        return json(object(field("count", meter.getCount()),
                           field("15-minute-rate", meter.getFifteenMinuteRate()),
                           field("5-minute-rate", meter.getFiveMinuteRate()),
                           field("1-minute-rate", meter.getOneMinuteRate()),
                           field("mean-rate", meter.getMeanRate())));
    }

    private static JsonValue counter(final Counter counter) {
        return json(counter.getCount());
    }

    private static JsonValue gauge(final Gauge<?> gauge) {
        return json(gauge.getValue());
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
