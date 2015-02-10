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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.http.protocol.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriFilterTest {

    private static final String REQUEST_URI = "http://www.forgerock.org/key_path";

    private Filter delegate;

    @Mock
    private Handler terminal;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        delegate = new DelegateFilter();
    }

    @Test
    public void shouldRebaseUri() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              Expression.valueOf("http://www.example.com:443"));

        final Exchange exchange = createExchangeAndSetUri();
        baseUriFilter.filter(exchange, terminal);

        verify(terminal).handle(exchange);

        assertThat(exchange.request.getUri().toString()).isEqualTo("http://www.example.com:443/key_path");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullUri() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              null);

        final Exchange exchange = createExchangeAndSetUri();
        baseUriFilter.filter(exchange, terminal);
    }

    @Test
    public void shouldNotRebaseWithEmptyUri() throws Exception {
        final BaseUriFilter baseUriFilter = new BaseUriFilter(delegate,
                                                              Expression.valueOf(""));

        final Exchange exchange = createExchangeAndSetUri();
        baseUriFilter.filter(exchange, terminal);

        verify(terminal).handle(exchange);

        assertThat(exchange.request.getUri().toString()).isEqualTo(REQUEST_URI);
    }

    private Exchange createExchangeAndSetUri() throws URISyntaxException {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri(REQUEST_URI);
        return exchange;
    }

    private static class DelegateFilter implements Filter {
        @Override
        public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
            next.handle(exchange);
        }
    }
}
