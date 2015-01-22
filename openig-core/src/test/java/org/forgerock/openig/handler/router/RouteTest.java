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

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.http.SessionFactory;
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
    private SessionFactory sessionFactory;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(sessionFactory.build(any(Exchange.class))).thenReturn(scoped);
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

        Exchange exchange = new Exchange();
        route.handle(exchange);

        verify(handler).handle(exchange);
    }

    @Test
    public void testSessionIsReplacingTheSessionForDownStreamHandlers() throws Exception {

        Route route = createRoute(sessionFactory, null);
        Exchange exchange = new Exchange();
        exchange.session = original;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                Exchange item = (Exchange) invocation.getArguments()[0];
                assertThat(item.session).isSameAs(scoped);
                return null;
            }
        }).when(handler).handle(exchange);

        route.handle(exchange);

        verify(handler).handle(exchange);
        assertThat(exchange.session).isSameAs(original);
    }

    @Test
    public void shouldReplaceBackTheOriginalSessionForUpStreamHandlersWhenExceptionsAreThrown() throws Exception {

        Route route = createRoute(sessionFactory, null);
        Exchange exchange = new Exchange();
        exchange.session = original;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                Exchange item = (Exchange) invocation.getArguments()[0];
                assertThat(item.session).isSameAs(scoped);
                throw new HandlerException();
            }
        }).when(handler).handle(exchange);

        try {
            route.handle(exchange);
            failBecauseExceptionWasNotThrown(HandlerException.class);
        } catch (Exception e) {
            assertThat(exchange.session).isSameAs(original);
        }

    }

    private Route createRoute(final SessionFactory sessionFactory,
                              final Expression condition) {
        return new Route(new HeapImpl(Name.of("anonymous")),
                         handler,
                         sessionFactory,
                         "router",
                         condition);
    }
}
