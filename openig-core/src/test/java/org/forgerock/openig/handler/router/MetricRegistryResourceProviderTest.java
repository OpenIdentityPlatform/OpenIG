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

import java.util.concurrent.TimeUnit;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.testng.annotations.Test;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

@SuppressWarnings("javadoc")
public class MetricRegistryResourceProviderTest {

    @Test
    public void shouldHandleGauges() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.register("gauge-1", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return 42;
            }
        });

        registry.register("gauge-2", new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return 53;
            }
        });

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("gauge-1")).asInteger()).isEqualTo(42);
        assertThat(response.get(ptr("gauge-2")).asInteger()).isEqualTo(53);
    }

    @Test
    public void shouldHandleCounters() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.counter("counter-1").inc();
        registry.counter("counter-2").inc(42);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("counter-1")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("counter-2")).asInteger()).isEqualTo(42);
    }

    @Test
    public void shouldHandleCounterGroups() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFilter.CounterGroup group1 = registry.register("group-1", new MetricsFilter.CounterGroup());
        group1.counter("all").inc();
        group1.counter("1xx").inc(42);
        MetricsFilter.CounterGroup group2 = registry.register("group-2", new MetricsFilter.CounterGroup());
        group2.counter("all").inc(53);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("group-1")).isMap()).isTrue();
        assertThat(response.get(ptr("group-1/all")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("group-1/1xx")).asInteger()).isEqualTo(42);
        assertThat(response.get(ptr("group-2")).isMap()).isTrue();
        assertThat(response.get(ptr("group-2/all")).asInteger()).isEqualTo(53);
    }

    @Test
    public void shouldHandleMeters() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.meter("meter-1").mark();
        registry.meter("meter-2").mark(42);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("meter-1/count")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("meter-1/mean-rate"))).isNotNull();
        assertThat(response.get(ptr("meter-1/15-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("meter-1/5-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("meter-1/1-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("meter-2"))).isNotNull();
    }

    @Test
    public void shouldHandleHistograms() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.histogram("histogram-1").update(1);
        registry.histogram("histogram-2").update(42);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("histogram-1/count")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("histogram-1/durations/mean"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/min"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/max"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/median"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/standard-deviation"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/percentiles/75th"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/percentiles/95th"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/percentiles/98th"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/percentiles/99th"))).isNotNull();
        assertThat(response.get(ptr("histogram-1/durations/percentiles/999th"))).isNotNull();
        assertThat(response.get(ptr("histogram-2"))).isNotNull();
    }

    @Test
    public void shouldHandleTimers() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.timer("timer-1").update(1, TimeUnit.SECONDS);
        registry.timer("timer-2").update(42, TimeUnit.SECONDS);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("timer-1/count")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("timer-1/mean-rate"))).isNotNull();
        assertThat(response.get(ptr("timer-1/15-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("timer-1/5-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("timer-1/1-minute-rate"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/mean"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/min"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/max"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/median"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/standard-deviation"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/percentiles/75th"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/percentiles/95th"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/percentiles/98th"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/percentiles/99th"))).isNotNull();
        assertThat(response.get(ptr("timer-1/durations/percentiles/999th"))).isNotNull();
        assertThat(response.get(ptr("timer-2"))).isNotNull();
    }

    @Test
    public void shouldHandleMetricSets() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.counter("counter").inc();

        MetricRegistry registry2 = new MetricRegistry();
        registry2.counter("counter").inc(2);

        MetricRegistry registry3 = new MetricRegistry();
        registry3.counter("counter").inc(3);

        registry.register("sub-1", registry2);
        registry.register("sub-2", registry3);

        MetricRegistryResourceProvider provider = new MetricRegistryResourceProvider(registry);
        JsonValue response = provider.readInstance(null, null).get().getContent();

        assertThat(response.get(ptr("counter")).asInteger()).isEqualTo(1);
        assertThat(response.get(ptr("sub-1.counter")).asInteger()).isEqualTo(2);
        assertThat(response.get(ptr("sub-2.counter")).asInteger()).isEqualTo(3);
    }


    private static JsonPointer ptr(final String pointer) {
        return new JsonPointer(pointer);
    }
}
