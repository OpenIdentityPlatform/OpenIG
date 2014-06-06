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
import static org.mockito.Mockito.*;

import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DispatchHandlerTest {

    @Test
    public void testDispatchWithRebasedURI() throws Exception {
        final DispatchHandler.Binding binding = new DispatchHandler.Binding();
        binding.condition = new Expression("${contains(exchange.request.uri.path,'/appA')}");
        binding.baseURI = new URI("http://hostA.domain.com");
        final Handler nextHandler = mock(Handler.class);
        binding.handler = nextHandler;

        final DispatchHandler handler = new DispatchHandler();
        handler.bindings.add(binding);

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://example.com/appA");

        handler.handle(exchange);

        verify(nextHandler).handle(exchange);
        assertThat(exchange.request.uri).isEqualTo(new URI("http://hostA.domain.com/appA"));
    }
}
