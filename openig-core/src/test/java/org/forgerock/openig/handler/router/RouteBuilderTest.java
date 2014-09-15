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

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.handler.router.Files.*;
import static org.forgerock.openig.io.TemporaryStorage.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.forgerock.openig.util.MutableUri.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;

import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.NullLogSink;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteBuilderTest {

    @Mock
    private Heap heap;

    @Mock
    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(heap.get(TEMPORARY_STORAGE_HEAP_KEY, TemporaryStorage.class)).thenReturn(new TemporaryStorage());
        when(heap.get(LOGSINK_HEAP_KEY, LogSink.class)).thenReturn(new NullLogSink());
    }

    @Test
    public void testUnnamedRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        Route route = builder.build(getTestResourceFile("route.json"));
        assertThat(route.getName()).isEqualTo("route.json");
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void testMissingHandlerRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        builder.build(getTestResourceFile("missing-handler-route.json"));
    }

    @Test
    public void testConditionalRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        Route route = builder.build(getTestResourceFile("conditional-route.json"));

        Exchange exchange = new Exchange();
        exchange.put("value", 42);
        assertThat(route.accept(exchange)).isTrue();
        exchange.put("value", 44);
        assertThat(route.accept(exchange)).isFalse();
    }

    @Test
    public void testNamedRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        Route route = builder.build(getTestResourceFile("named-route.json"));
        assertThat(route.getName()).isEqualTo("my-route");
    }

    @Test
    public void testRebaseUriRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        Route route = builder.build(getTestResourceFile("rebase-uri-route.json"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://openig.forgerock.org/demo");

        route.handle(exchange);

        assertThat(exchange.request.getUri()).isEqualTo(uri("https://localhost:443/demo"));
    }

    @Test
    public void testSessionRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap);
        Route route = builder.build(getTestResourceFile("session-route.json"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.session = new SimpleMapSession();

        route.handle(exchange);

        assertThat(exchange.session).isEmpty();
        assertThat(exchange.response.getHeaders().getFirst("Set-Cookie")).isNotNull();

    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void close() throws IOException { }
    }

    public static class SessionHandler extends GenericHandler {

        @Override
        public void handle(final Exchange exchange) throws HandlerException, IOException {
            exchange.response = new Response();
            exchange.session.put("ForgeRock", "OpenIG");
        }

        public static class Heaplet extends GenericHeaplet {

            @Override
            public Object create() throws HeapException {
                return new SessionHandler();
            }
        }
    }

}
