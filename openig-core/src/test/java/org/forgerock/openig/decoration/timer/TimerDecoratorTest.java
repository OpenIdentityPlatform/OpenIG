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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.openig.heap.Keys.TICKER_HEAP_KEY;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TimerDecoratorTest {

    private String name;

    @Mock
    private Filter filter;

    @Mock
    private Handler handler;

    @Mock
    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        heap.put(TICKER_HEAP_KEY, Ticker.systemTicker());
        when(context.getHeap()).thenReturn(heap);
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));
        name = "myTimerDecorator";
    }

    @SuppressWarnings("unused")
    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWhenNullTimeUnitIsProvided() {
        new TimerDecorator(name, null);
    }


    @DataProvider
    public Object[][] timerDecorator() {
        return new Object[][] {
            { new TimerDecorator(name) },
            { new TimerDecorator(name, TimeUnit.MICROSECONDS)} };
    }

    @Test(dataProvider = "timerDecorator")
    public void shouldDecorateFilter(final TimerDecorator timerDecorator) throws Exception {
        Object decorated = timerDecorator.decorate(filter, json(true), context);
        assertThat(decorated).isInstanceOf(TimerFilter.class);
    }

    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("timeUnit", "nano"))) },
            { json(object(
                    field("timeUnit", "nanoseconds"))) },
            { json(object(
                    field("timeUnit", "ns"))) },
            { json(object()) } };
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final TimerDecorator.Heaplet heaplet = new TimerDecorator.Heaplet();
        final TimerDecorator timerDecorator = (TimerDecorator) heaplet.create(Name.of(name),
                                                                              config,
                                                                              buildDefaultHeap());
        assertThat(timerDecorator).isNotNull();
    }

    @DataProvider
    private static Object[][] invalidConfigurations() {
        return new Object[][] {
            { json(object(
                    field("timeUnit", "invalid"))) },
            { json(object(
                    field("timeUnit", "0 seconds"))) },
            { json(object(
                    field("timeUnit", "ZERO"))) },
            { json(object(
                    field("timeUnit", "UNLIMITED"))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = HeapException.class)
    public void shouldFailToCreateHeaplet(final JsonValue config) throws Exception {
        final TimerDecorator.Heaplet heaplet = new TimerDecorator.Heaplet();
        heaplet.create(Name.of(name), config, buildDefaultHeap());
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        TimerDecorator decorator = new TimerDecorator(name);

        Object decorated = decorator.decorate(handler, json(true), context);
        assertThat(decorated).isInstanceOf(TimerHandler.class);
    }

    @Test
    public void shouldNotDecorateFilter() throws Exception {
        TimerDecorator decorator = new TimerDecorator(name);

        Object decorated = decorator.decorate(filter, json(false), context);
        assertThat(decorated).isSameAs(filter);
    }

    @Test
    public void shouldNotDecorateHandler() throws Exception {
        TimerDecorator decorator = new TimerDecorator(name);

        Object decorated = decorator.decorate(handler, json(false), context);
        assertThat(decorated).isSameAs(handler);
    }

    @DataProvider
    public static Object[][] undecoratableObjects() {
        // @Checkstyle:off
        return new Object[][] {
                {"a string"},
                {42},
                {new ArrayList<>()}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "undecoratableObjects")
    public void shouldNotDecorateUnsupportedTypes(Object o) throws Exception {
        assertThat(new TimerDecorator(name).decorate(o, null, context)).isSameAs(o);
    }
}
