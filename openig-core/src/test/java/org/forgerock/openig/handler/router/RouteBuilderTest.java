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

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.MutableUri.*;
import static org.forgerock.openig.handler.router.Files.*;
import static org.forgerock.openig.heap.HeapImplTest.*;

import java.io.IOException;
import java.util.HashMap;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpContext;
import org.forgerock.http.RootContext;
import org.forgerock.http.Session;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteBuilderTest {

    private HeapImpl heap;

    @Mock
    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        heap = buildDefaultHeap();
    }

    @Test(description = "OPENIG-329")
    public void testHandlerCanBeInlinedWithNoHeap() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("inlined-handler-route.json"));

        Exchange exchange = new Exchange();

        assertThat(route.handle(exchange, new Request())
                        .get()
                        .getStatus()).isEqualTo(42);
    }

    @Test
    public void testUnnamedRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("route.json"));
        assertThat(route.getName()).isEqualTo("route.json");
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void testMissingHandlerRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        builder.build(getTestResourceFile("missing-handler-route.json"));
    }

    @Test
    public void testConditionalRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("conditional-route.json"));

        Exchange exchange = new Exchange();
        exchange.put("value", 42);
        assertThat(route.accept(exchange)).isTrue();
        exchange.put("value", 44);
        assertThat(route.accept(exchange)).isFalse();
    }

    @Test
    public void testNamedRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("named-route.json"));
        assertThat(route.getName()).isEqualTo("my-route");
    }

    @Test
    public void testRebaseUriRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("rebase-uri-route.json"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://openig.forgerock.org/demo");

        route.handle(exchange, exchange.request);

        assertThat(exchange.request.getUri()).isEqualTo(uri("https://localhost:443/demo"));
    }

    @Test
    public void testSessionRouteLoading() throws Exception {
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"));
        Route route = builder.build(getTestResourceFile("session-route.json"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.session = new SimpleMapSession();
        HttpContext httpContext = new HttpContext(new RootContext(), exchange.session);
        exchange.parent = httpContext;


        assertThat(route.handle(exchange, exchange.request)
                        .get()
                        .getHeaders()
                        .getFirst("Set-Cookie")).isNotNull();
        assertThat(httpContext.getSession()).isEmpty();

    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }

    public static class SessionHandler implements Handler {

        @Override
        public Promise<Response, ResponseException> handle(final Context context, final Request request) {
            Session session = context.asContext(HttpContext.class).getSession();
            session.put("ForgeRock", "OpenIG");
            return Promises.newSuccessfulPromise(new Response());
        }

        public static class Heaplet extends GenericHeaplet {

            @Override
            public Object create() throws HeapException {
                return new SessionHandler();
            }
        }
    }

}
