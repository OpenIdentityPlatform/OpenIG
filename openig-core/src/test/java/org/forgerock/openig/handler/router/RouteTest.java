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
import static org.mockito.Mockito.*;

import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteTest {

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRouteAcceptingTheExchange() throws Exception {
        Route route = createRoute(new Expression("${true}"), null);
        assertThat(route.accept(new Exchange())).isTrue();
    }

    @Test
    public void testRouteRejectingTheExchange() throws Exception {
        Route route = createRoute(new Expression("${false}"), null);
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
    public void testRouteIsRebasingTheRequestUri() throws Exception {
        Route route = createRoute(null, new URI("https://localhost:443"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://openig.forgerock.org/demo");

        route.handle(exchange);

        assertThat(exchange.request.uri).isEqualTo(new URI("https://localhost:443/demo"));
    }

    private Route createRoute(final Expression condition, final URI baseURI) {
        return new Route(new HeapImpl(),
                         handler,
                         "router",
                         condition,
                         baseURI);
    }
}
