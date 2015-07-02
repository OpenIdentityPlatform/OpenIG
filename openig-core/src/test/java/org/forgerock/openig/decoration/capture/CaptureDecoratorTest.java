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

package org.forgerock.openig.decoration.capture;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.decoration.helper.LazyReference.*;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.helper.LazyReference;
import org.forgerock.openig.heap.HeapException;
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
public class CaptureDecoratorTest {

    @Mock
    private Filter filter;

    @Mock
    private Handler handler;

    @Mock
    private Context context;

    private LazyReference<LogSink> reference;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        when(context.getHeap()).thenReturn(heap);
        when(context.getConfig()).thenReturn(json(emptyMap()));
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));

        reference = newReference(heap, json(null), LogSink.class, true);
    }

    @DataProvider
    public static Object[][] modeEnumWithDifferentCases() {
        // @Checkstyle:off
        return new Object[][] {
                {"aLl"},
                {"ReQuest"},
                {"reSPONse"},
                {"filtered_REQUEST"},
                {"filtered_RESPONSE"},
                {"FILTERED_request"},
                {"FILTERED_response"}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "modeEnumWithDifferentCases")
    public void shouldReadEnumFromDecorationConfig(String name) throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(name), context);
    }

    @DataProvider
    public static Object[][] invalidModeNames() {
        // @Checkstyle:off
        return new Object[][] {
                {""},
                {"who-are-you ?"},
                {"incoming-request"},
                {"outgoing-response"}
        };
        // @Checkstyle:on
    }

    @Test(expectedExceptions = HeapException.class)
    public void shouldFailWithNullCapturePointsConfiguration() throws Exception {
        final CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(null), context);
    }

    @Test(dataProvider = "invalidModeNames",
          expectedExceptions = IllegalArgumentException.class)
    public void shouldFailForInvalidModes(String name) throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(name), context);
    }

    @Test(expectedExceptions = HeapException.class)
    public void shouldFailWithInvalidJsonObjectCapturePointsConfiguration() throws Exception {
        final CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(object(field("Should NOT be", "an object"))), context);
    }

    @Test(expectedExceptions = HeapException.class)
    public void shouldFailWithJsonArrayCapturePointsConfigurationContainingNull() throws Exception {
        final CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(array(null, "response")), context);
    }

    @Test
    public void shouldReadMultipleCapturePointsSpecified() throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        decorator.decorate(filter, json(array("request", "response")), context);
    }

    @Test
    public void shouldNotDecorateWhenNoCapturePointsAreSpecified() throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        assertThat(decorator.decorate(filter, json(array()), context)).isSameAs(filter);
    }

    @Test
    public void shouldDecorateFilter() throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);

        Object decorated = decorator.decorate(filter, json("all"), context);
        assertThat(decorated).isInstanceOf(CaptureFilter.class);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);

        Object decorated = decorator.decorate(handler, json("all"), context);
        assertThat(decorated).isInstanceOf(CaptureHandler.class);
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
        CaptureDecorator decorator = new CaptureDecorator(reference, false, false);
        assertThat(decorator.decorate(o, json("all"), context)).isSameAs(o);
    }

    @Test
    public void shouldSupportNullLogSinkReference() throws Exception {
        // This case reproduce NPE when the CaptureDecorator is created in the
        // GatewayServlet (default capture decorator) with no LazyReference<LogSink>
        // provided (meaning it will output messages in each component's LogSink)
        CaptureDecorator decorator = new CaptureDecorator(null, false, false);
        decorator.decorate(filter, json("all"), context);
    }
}
