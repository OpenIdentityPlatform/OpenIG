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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.NullLogSink;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TimerDecoratorTest {
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
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        when(context.getHeap()).thenReturn(heap);
        when(context.getConfig()).thenReturn(json(emptyMap()));
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));
    }

    @Test
    public void shouldDecorateFilter() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

        Object decorated = decorator.decorate(filter, json(true), context);
        assertThat(decorated).isInstanceOf(TimerFilter.class);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

        Object decorated = decorator.decorate(handler, json(true), context);
        assertThat(decorated).isInstanceOf(TimerHandler.class);
    }

    @Test
    public void shouldNotDecorateFilter() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

        Object decorated = decorator.decorate(filter, json(false), context);
        assertThat(decorated).isSameAs(filter);
    }

    @Test
    public void shouldNotDecorateHandler() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

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
        TimerDecorator decorator = new TimerDecorator();
        assertThat(decorator.decorate(o, null, context)).isSameAs(o);
    }
}
