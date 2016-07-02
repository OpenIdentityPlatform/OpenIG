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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ChainFilterHeapletTest {

    @DataProvider(name = "invalidConfigurations")
    public static Object[][] invalidConfigurations() {
        //@Checkstyle:off
        return new Object[][]{
                // No filters
                { json(object()) },
                // Not known filter
                { json(object(field("filters", array("myFilter")))) }
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldThrowAnExceptionOnInvalidConfiguration(JsonValue config) throws Exception {
        ChainHandlerHeaplet heaplet = new ChainHandlerHeaplet();
        heaplet.create(Name.of("chain-test"), config, buildHeap());
    }

    @Test
    public void shouldCreateFilter() throws Exception {
        JsonValue config = json(object(field("filters", array("filter1", "filter2"))));

        ChainFilterHeaplet heaplet = new ChainFilterHeaplet();
        Object created = heaplet.create(Name.of("chain-test"), config, buildHeap());

        assertThat(created).isInstanceOf(Filter.class);

        Filter filter = (Filter) created;
        Handler handler = mock(Handler.class);
        Request request = new Request();
        filter.filter(new RootContext(), request, handler);

        assertThat(request.getHeaders().copyAsMultiMapOfStrings())
                .containsExactly(entry("Filter-Header-1", singletonList("1")),
                                 entry("Filter-Header-2", singletonList("2")));
        verify(handler).handle(any(Context.class), any(Request.class));
    }

    private HeapImpl buildHeap() throws Exception {
        HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("filter1", new SampleFilter("1"));
        heap.put("filter2", new SampleFilter("2"));
        return heap;
    }

    private static class SampleFilter implements Filter {
        private final String s;

        public SampleFilter(String s) {
            this.s = s;
        }

        @Override
        public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler handler) {
            request.getHeaders().put("Filter-Header-" + s, s);
            return handler.handle(context, request);
        }
    }
}
