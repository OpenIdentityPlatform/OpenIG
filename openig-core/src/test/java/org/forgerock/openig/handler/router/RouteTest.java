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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteTest {

    private static final String ROUTE_NAME = "my-route";

    @Mock
    private Handler handler;

    private PromiseImpl<Response, NeverThrowsException> promise = PromiseImpl.create();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(handler.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise);
    }

    @Test
    public void testRouteAcceptingTheRequest() throws Exception {
        Route route = createRoute(Expression.valueOf("${true}", Boolean.class));
        assertThat(route.accept(new RootContext(), null)).isTrue();
    }

    @Test
    public void testRouteRejectingTheRequest() throws Exception {
        Route route = createRoute(Expression.valueOf("${false}", Boolean.class));
        assertThat(route.accept(new RootContext(), null)).isFalse();
    }

    @Test
    public void testRouteIsDelegatingTheRequest() throws Exception {
        Route route = createRoute(null);
        assertThat(route.handle(new RootContext(), new Request())).isSameAs(promise);
    }

    private Route createRoute(final Expression<Boolean> condition) {
        return new Route(handler, ROUTE_NAME, ROUTE_NAME, json(object()), condition) {
            @Override
            public void start() { }
            @Override
            public void destroy() { }
        };
    }
}
