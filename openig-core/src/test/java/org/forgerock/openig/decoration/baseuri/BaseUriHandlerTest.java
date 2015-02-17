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

import java.net.URISyntaxException;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BaseUriHandlerTest {

    private static final String REQUEST_URI = "http://www.forgerock.org/key_path";

    @Mock
    private Handler delegate;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldRebaseUri() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          Expression.valueOf("http://www.example.com:443",
                                                                  String.class));

        final Exchange exchange = createExchangeAndSetUri();
        handler.handle(exchange);

        verify(delegate).handle(exchange);

        assertThat(exchange.request.getUri().toString()).isEqualTo("http://www.example.com:443/key_path");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullUri() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          null);

        final Exchange exchange = createExchangeAndSetUri();
        handler.handle(exchange);

        verify(delegate).handle(exchange);
    }

    @Test
    public void shouldNotRebaseWithEmptyUri() throws Exception {
        final BaseUriHandler handler = new BaseUriHandler(delegate,
                                                          Expression.valueOf("", String.class));

        final Exchange exchange = createExchangeAndSetUri();
        handler.handle(exchange);

        verify(delegate).handle(exchange);

        assertThat(exchange.request.getUri().toString()).isEqualTo("http://www.forgerock.org/key_path");
    }

    private Exchange createExchangeAndSetUri() throws URISyntaxException {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri(REQUEST_URI);
        return exchange;
    }
}
