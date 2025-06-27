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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.TEAPOT;
import static org.forgerock.json.JsonValue.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriDecoratorTest {

    private String name;

    @Mock
    private Filter filter;

    @Mock
    private Handler handler;

    @Mock
    private org.forgerock.openig.decoration.Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        when(context.getHeap()).thenReturn(heap);
        when(context.getConfig()).thenReturn(json(emptyMap()));
        when(context.getName()).thenReturn(Name.of("config.json", "Router"));
        name = "myDecoratedObject";
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
        filter = spy(new Filter() {
            @Override
            public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
                assertThat(request.getUri().toASCIIString()).isEqualTo("http://localhost:80/foo");
                return next.handle(context, request);
            }
        });

        final Object decorated = new BaseUriDecorator(name).decorate(filter,
                                                                     json("http://localhost:80"),
                                                                     context);
        assertThat(decorated).isInstanceOf(Filter.class);
        Filter decoratedHandler = (Filter) decorated;
        Request request = new Request();
        request.setMethod("GET").setUri("http://www.forgerock.org:8080/foo");

        Response response = decoratedHandler.filter(new RootContext(), request, new ResponseHandler(TEAPOT)).get();

        assertThat(response.getStatus()).isEqualTo(TEAPOT);
    }

    @Test
    public void shouldDecorateHandler() throws Exception {
        when(handler.handle(any(Context.class), any(Request.class)))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(InvocationOnMock invocation)
                            throws Throwable {
                        Request request = (Request) invocation.getArguments()[1];
                        assertThat(request.getUri().toASCIIString()).isEqualTo("http://localhost:80/foo");
                        return newResponsePromise(new Response(TEAPOT));
                    }
                });

        final Object decorated = new BaseUriDecorator(name).decorate(handler,
                                                                     json("http://localhost:80"),
                                                                     context);
        assertThat(decorated).isInstanceOf(Handler.class);
        Handler decoratedHandler = (Handler) decorated;
        Request request = new Request();
        request.setMethod("GET").setUri("http://www.forgerock.org:8080/foo");

        Response response = decoratedHandler.handle(new RootContext(), request).get();

        assertThat(response.getStatus()).isEqualTo(TEAPOT);
    }

    @Test
    public void shouldNotDecorateFilter() throws Exception {
        final Object decorated = new BaseUriDecorator(name).decorate(filter, json(false), context);
        assertThat(decorated).isSameAs(filter);
    }

    @Test
    public void shouldNotDecorateHandler() throws Exception {
        final Object decorated = new BaseUriDecorator(name).decorate(handler, json(false), context);
        assertThat(decorated).isSameAs(handler);
    }

    @Test(dataProvider = "undecoratableObjects")
    public void shouldNotDecorateUnsupportedTypes(Object o) throws Exception {
        assertThat(new BaseUriDecorator(name).decorate(o, null, context)).isSameAs(o);
    }
}
