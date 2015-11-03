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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.http.session.SessionManager;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
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

    private PromiseImpl<Response, NeverThrowsException> promise = PromiseImpl.create();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(sessionManager.load(any(Request.class))).thenReturn(scoped);
        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise);
    }

    @Test
    public void testRouteAcceptingTheRequest() throws Exception {
        Route route = createRoute(null, Expression.valueOf("${true}", Boolean.class));
        assertThat(route.accept(new RootContext(), null)).isTrue();
    }

    @Test
    public void testRouteRejectingTheRequest() throws Exception {
        Route route = createRoute(null, Expression.valueOf("${false}", Boolean.class));
        assertThat(route.accept(new RootContext(), null)).isFalse();
    }

    @Test
    public void testRouteIsDelegatingTheRequest() throws Exception {
        Route route = createRoute(null, null);
        assertThat(route.handle(new RootContext(), new Request())).isSameAs(promise);
    }

    @Test
    public void testSessionIsReplacingTheSessionForDownStreamHandlers() throws Exception {

        Route route = createRoute(sessionManager, null);
        Context context = new SessionContext(new RootContext(), original);

        when(handler.handle(context, new Request()))
                .then(new Answer<Void>() {
                    @Override
                    public Void answer(final InvocationOnMock invocation) throws Throwable {
                        Context context = (Context) invocation.getArguments()[0];
                        assertThat(context.asContext(SessionContext.class).getSession()).isSameAs(scoped);
                        return null;
                    }
                });

        route.handle(context, new Request());
        promise.handleResult(new Response());

        assertThat(context.asContext(SessionContext.class).getSession()).isSameAs(original);
    }

    private Route createRoute(final SessionManager sessionManager,
                              final Expression<Boolean> condition) {
        return new Route(new HeapImpl(Name.of("anonymous")),
                         handler,
                         sessionManager,
                         "router",
                         condition);
    }
}
