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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.openig.decoration.helper.LazyReference.newReference;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.helper.LazyReference;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.NullLogSink;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriDecoratorTest {

    private LazyReference<LogSink> reference;

    @Mock
    private Filter filter;

    @Mock
    private LogSink logSink;

    @Mock
    private Handler handler;

    @Mock
    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        when(context.getHeap()).thenReturn(heap);
        when(context.getConfig()).thenReturn(json(emptyMap()));
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));
        reference = newReference(heap, json(null), LogSink.class, true);
    }

    @DataProvider
    public static Object[][] undecoratableObjects() {
        return new Object[][] {
            { "a string" },
            { 42 },
            {new ArrayList<>() }
        };
    }

    @Test
    public void shouldDecorateFilter() throws Exception {
        final Object decorated = new BaseUriDecorator(reference).decorate(filter, json("http://localhost:80"), context);
        assertThat(decorated).isInstanceOf(BaseUriFilter.class);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        final Object decorated = new BaseUriDecorator(reference).decorate(handler,
                                                                          json("http://localhost:80"),
                                                                          context);
        assertThat(decorated).isInstanceOf(BaseUriHandler.class);
    }

    @Test
    public void shouldNotDecorateFilter() throws Exception {
        final Object decorated = new BaseUriDecorator(reference).decorate(filter, json(false), context);
        assertThat(decorated).isSameAs(filter);
    }

    @Test
    public void shouldNotDecorateHandler() throws Exception {
        final Object decorated = new BaseUriDecorator(reference).decorate(handler, json(false), context);
        assertThat(decorated).isSameAs(handler);
    }

    @Test(dataProvider = "undecoratableObjects")
    public void shouldNotDecorateUnsupportedTypes(Object o) throws Exception {
        assertThat(new BaseUriDecorator(reference).decorate(o, null, context)).isSameAs(o);
    }

    @Test
    public void shouldSupportNullLogSinkReference() throws Exception {
        BaseUriDecorator decorator = new BaseUriDecorator(null);
        decorator.decorate(filter, json("all"), context);
    }
}
