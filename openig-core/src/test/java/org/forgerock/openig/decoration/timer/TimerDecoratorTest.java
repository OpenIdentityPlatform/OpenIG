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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.HeapImpl;
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
        HeapImpl heap = new HeapImpl();
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        when(context.getHeap()).thenReturn(heap);
        when(context.getConfig()).thenReturn(json(emptyMap()));
    }

    @Test
    public void shouldDecorateFilter() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

        Object decorated = decorator.decorate(filter, null, context);
        assertThat(decorated).isInstanceOf(TimerFilter.class);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        TimerDecorator decorator = new TimerDecorator();

        Object decorated = decorator.decorate(handler, null, context);
        assertThat(decorated).isInstanceOf(TimerHandler.class);
    }

    @DataProvider
    public static Object[][] undecoratableObjects() {
        // @Checkstyle:off
        return new Object[][] {
                {"a string"},
                {42},
                {new ArrayList<Object>()}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "undecoratableObjects")
    public void shouldNotDecorateUnsupportedTypes(Object o) throws Exception {
        TimerDecorator decorator = new TimerDecorator();
        assertThat(decorator.decorate(o, null, context)).isSameAs(o);
    }
}
