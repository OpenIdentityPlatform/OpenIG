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
import static org.mockito.Mockito.*;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpContext;
import org.forgerock.http.RootContext;
import org.forgerock.http.Session;
import org.forgerock.http.SessionManager;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteTest {

    @Mock
    private Handler handler;

    @Mock
    private Session original;

    @Mock
    private Session scoped;

    @Mock
    private SessionManager sessionManager;

    private PromiseImpl<Response, ResponseException> promise = PromiseImpl.create();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(sessionManager.load(any(Request.class))).thenReturn(scoped);
        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise);
    }

    @Test
    public void testRouteAcceptingTheExchange() throws Exception {
        Route route = createRoute(null, Expression.valueOf("${true}"));
        assertThat(route.accept(new Exchange())).isTrue();
    }

    @Test
    public void testRouteRejectingTheExchange() throws Exception {
        Route route = createRoute(null, Expression.valueOf("${false}"));
        assertThat(route.accept(new Exchange())).isFalse();
    }

    @Test
    public void testRouteIsDelegatingTheExchange() throws Exception {
        Route route = createRoute(null, null);
        assertThat(route.handle(new Exchange(), new Request())).isSameAs(promise);
    }

    @Test
    public void testSessionIsReplacingTheSessionForDownStreamHandlers() throws Exception {

        Route route = createRoute(sessionManager, null);
        Exchange exchange = new Exchange();
        exchange.parent = new HttpContext(new RootContext(), original);

        when(handler.handle(exchange, new Request()))
                .then(new Answer<Void>() {
                    @Override
                    public Void answer(final InvocationOnMock invocation) throws Throwable {
                        Context context = (Context) invocation.getArguments()[0];
                        assertThat(context.asContext(HttpContext.class).getSession()).isSameAs(scoped);
                        return null;
                    }
                });

        route.handle(exchange, new Request());
        promise.handleResult(new Response());

        assertThat(exchange.asContext(HttpContext.class).getSession()).isSameAs(original);
    }

    @Test
    public void shouldReplaceBackTheOriginalSessionForUpStreamHandlersWhenExceptionsAreThrown() throws Exception {

        Route route = createRoute(sessionManager, null);
        Exchange exchange = new Exchange();
        exchange.parent = new HttpContext(new RootContext(), original);

        when(handler.handle(exchange, new Request()))
                .then(new Answer<Void>() {
                    @Override
                    public Void answer(final InvocationOnMock invocation) throws Throwable {
                        Context context = (Context) invocation.getArguments()[0];
                        assertThat(context.asContext(HttpContext.class).getSession()).isSameAs(scoped);
                        return null;
                    }
                });

        promise.handleError(new ResponseException(500));
        Promise<Response, ResponseException> result = route.handle(exchange, new Request());

        try {
            result.getOrThrow();
            failBecauseExceptionWasNotThrown(ResponseException.class);
        } catch (Exception e) {
            assertThat(exchange.asContext(HttpContext.class).getSession()).isSameAs(original);
        }

    }

    private Route createRoute(final SessionManager sessionManager,
                              final Expression condition) {
        return new Route(new HeapImpl(Name.of("anonymous")),
                         handler,
                         sessionManager,
                         "router",
                         condition);
    }
}
