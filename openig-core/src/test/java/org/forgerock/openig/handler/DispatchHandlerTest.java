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
package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.util.MutableUri.*;
import static org.mockito.Mockito.*;

import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This class is unit testing the dispatch handler class.
 */
@SuppressWarnings("javadoc")
public class DispatchHandlerTest {

    /** Part of the URI we looking for. */
    private static final String URI_PART = "/key_path";

    /** The condition to dispatch the handler. */
    private static final String CONDITION = String.format("${contains(exchange.request.uri.path,'%s')}", URI_PART);

    @Mock
    private Handler nextHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDispatchWithRebasedUriStandard() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(new Expression(CONDITION), nextHandler, new URI("http://www.hostA.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.example.com/key_path");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);
        assertThat(exchange.request.getUri()).isEqualTo(uri("http://www.hostA.domain.com/key_path"));
    }

    @Test
    public void testDispatchWithRebasedUriWithUserInfo() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(new Expression(CONDITION), nextHandler, new URI("http://www.hostA.domain.com:443"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://user.0:password@www.example.com/key_path");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);
        assertThat(exchange.request.getUri()).isEqualTo(uri(
                "http://user.0:password@www.hostA.domain.com:443/key_path"));
    }

    @Test
    public void testDispatchWithRebasedUriWithSchemeAndQueryAndFragment() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(new Expression(CONDITION), nextHandler, new URI("https://www.hostA.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.example.com:40/key_path?query=true&name=b%20jensen#20");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);
        assertThat(exchange.request.getUri()).isEqualTo(uri(
                "https://www.hostA.domain.com/key_path?query=true&name=b%20jensen#20"));
    }

    @Test
    public void testDispatchWithRebasedURIUnconditionalDispatch() throws Exception {
        final DispatchHandler dispatchHandler = new DispatchHandler();
        // unconditional dispatch when expression is null.
        dispatchHandler.addUnconditionalBinding(nextHandler, new URI("https://www.hostB.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.example.com:40/key_path/path");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);
        assertThat(exchange.request.getUri()).isEqualTo(uri("https://www.hostB.domain.com/key_path/path"));
    }

    @Test
    public void testDispatchWithRebasedURI() throws Exception {
        final Expression expression = new Expression("${contains(exchange.request.uri.host,'this.domain') and "
                + "contains(exchange.request.uri.path,'/user.0')}");

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(expression, nextHandler, new URI("https://www.secure.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.this.domain.com/data/user.0");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);

        assertThat(exchange.request.getUri()).isEqualTo(uri("https://www.secure.domain.com/data/user.0"));
    }

    @Test
    public void testDispatchWithNullBaseURI() throws Exception {
        final Expression expression = new Expression("${contains(exchange.request.uri.host,'this.domain') and "
                + "contains(exchange.request.uri.path,'/user.0')}");

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(expression, nextHandler, null);

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.this.domain.com/data/user.0");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);

        assertThat(exchange.request.getUri()).isEqualTo(uri("http://www.this.domain.com/data/user.0"));
    }

    @Test
    public void testDispatchWithMultipleBindings() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(new Expression(CONDITION), nextHandler, new URI("https://www.hostA.domain.com"));
        dispatchHandler.addUnconditionalBinding(nextHandler, new URI("https://www.hostB.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://www.example.com/");

        dispatchHandler.handle(exchange);

        verify(nextHandler).handle(exchange);
        // Only the first verified binding applies.
        assertThat(exchange.request.getUri()).isEqualTo(uri("https://www.hostB.domain.com/"));

        // Now with an URI which verifies the first condition
        exchange.request.setUri("http://www.example.com/key_path");
        dispatchHandler.handle(exchange);
        verify(nextHandler, times(2)).handle(exchange);
        assertThat(exchange.request.getUri()).isEqualTo(uri("https://www.hostA.domain.com/key_path"));

    }


    @Test(expectedExceptions = HandlerException.class)
    public void testDispatchNoHandlerToDispatch() throws Exception {
        new DispatchHandler().handle(new Exchange());
    }
}
