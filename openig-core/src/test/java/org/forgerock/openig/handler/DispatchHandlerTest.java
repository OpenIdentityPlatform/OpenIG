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
package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.MutableUri.uri;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
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
    private static final String CONDITION = String.format("${contains(request.uri.path,'%s')}", URI_PART);

    @Mock
    private Handler nextHandler;

    @Mock
    private Promise<Response, NeverThrowsException> promise;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(nextHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise);
    }

    @Test
    public void testDispatchWithRebasedUriStandard() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(Expression.valueOf(CONDITION, Boolean.class),
                nextHandler, new URI("http://www.hostA.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.example.com/key_path");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("http://www.hostA.domain.com/key_path"));
    }

    @Test
    public void testDispatchWithRebasedUriWithUserInfo() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(Expression.valueOf(CONDITION, Boolean.class),
                                    nextHandler,
                                    new URI("http://www.hostA.domain.com:443"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://user.0:password@www.example.com/key_path");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri(
                "http://user.0:password@www.hostA.domain.com:443/key_path"));
    }

    @Test
    public void testDispatchWithRebasedUriWithSchemeAndQueryAndFragment() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(Expression.valueOf(CONDITION, Boolean.class), nextHandler,
                new URI("https://www.hostA.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.example.com:40/key_path?query=true&name=b%20jensen#20");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri(
                "https://www.hostA.domain.com/key_path?query=true&name=b%20jensen#20"));
    }

    @Test
    public void testDispatchWithRebasedURIUnconditionalDispatch() throws Exception {
        final DispatchHandler dispatchHandler = new DispatchHandler();
        // unconditional dispatch when expression is null.
        dispatchHandler.addUnconditionalBinding(nextHandler, new URI("https://www.hostB.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.example.com:40/key_path/path");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("https://www.hostB.domain.com/key_path/path"));
    }

    @Test
    public void testDispatchWithRebasedURI() throws Exception {
        final Expression<Boolean> expression = Expression.valueOf("${contains(request.uri.host,'this.domain') "
                + "and contains(request.uri.path,'/user.0')}", Boolean.class);

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(expression, nextHandler, new URI("https://www.secure.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.this.domain.com/data/user.0");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());

        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("https://www.secure.domain.com/data/user.0"));
    }

    @Test
    public void testDispatchWithNullBaseURI() throws Exception {
        final Expression<Boolean> expression = Expression.valueOf("${contains(request.uri.host,'this.domain') "
                + "and contains(request.uri.path,'/user.0')}", Boolean.class);

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(expression, nextHandler, null);

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.this.domain.com/data/user.0");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());

        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("http://www.this.domain.com/data/user.0"));
    }

    @Test
    public void testDispatchWithMultipleBindings() throws Exception {

        final DispatchHandler dispatchHandler = new DispatchHandler();
        dispatchHandler.addBinding(Expression.valueOf(CONDITION, Boolean.class), nextHandler,
                new URI("https://www.hostA.domain.com"));
        dispatchHandler.addUnconditionalBinding(nextHandler, new URI("https://www.hostB.domain.com"));

        final Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://www.example.com/");

        dispatchHandler.handle(exchange, exchange.getRequest());

        verify(nextHandler).handle(exchange, exchange.getRequest());
        // Only the first verified binding applies.
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("https://www.hostB.domain.com/"));

        // Now with an URI which verifies the first condition
        exchange.getRequest().setUri("http://www.example.com/key_path");
        dispatchHandler.handle(exchange, exchange.getRequest());
        verify(nextHandler, times(2)).handle(exchange, exchange.getRequest());
        assertThat(exchange.getRequest().getUri()).isEqualTo(uri("https://www.hostA.domain.com/key_path"));

    }

    @Test
    public void testDispatchNoHandlerToDispatch() throws Exception {
        Response response = new DispatchHandler().handle(new Exchange(), new Request()).get();
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        assertThat(response.getEntity().getString()).contains("no handler to dispatch to");
    }
}
