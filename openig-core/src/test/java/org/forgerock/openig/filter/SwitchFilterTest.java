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

package org.forgerock.openig.filter;

import static org.mockito.Mockito.*;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SwitchFilterTest {

    private Exchange exchange = new Exchange();

    @Mock
    private Handler terminalHandler;

    @Mock
    private Handler handler1;

    @Mock
    private Handler handler2;

    @Mock
    private Handler handler3;

    @Mock
    private Handler handler4;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSwitchOnRequestOnly() throws Exception {
        SwitchFilter filter = new SwitchFilter();
        filter.onRequest.add(buildCase("${true}", handler1));

        filter.filter(exchange, terminalHandler);

        // Filter's handler should not be called because the stream has been diverted
        verifyZeroInteractions(terminalHandler);
        verify(handler1).handle(exchange);
    }

    @Test
    public void testSwitchOnResponseOnly() throws Exception {
        SwitchFilter filter = new SwitchFilter();
        filter.onResponse.add(buildCase("${true}", handler1));

        filter.filter(exchange, terminalHandler);

        // No request conditions were met (there was none), so the responses cases are tried
        // and the case's handler that evaluated to true is being executed
        InOrder inOrder = inOrder(terminalHandler, handler1);
        inOrder.verify(terminalHandler).handle(exchange);
        inOrder.verify(handler1).handle(exchange);
    }

    @Test
    public void testResponseHandlerNotInvokedIfRequestHasBeenDiverted() throws Exception {
        SwitchFilter filter = new SwitchFilter();

        // Expect the request's case to divert the flow and ignore the response's case
        filter.onRequest.add(buildCase("${true}", handler1));
        filter.onResponse.add(buildCase("${true}", handler2));

        filter.filter(exchange, terminalHandler);

        // As the request condition is fulfilled, the handler plugged onto
        // the response should not be called
        verify(handler1).handle(exchange);
        verifyZeroInteractions(terminalHandler);
        verifyZeroInteractions(handler2);
    }

    @Test
    public void testThatOnlyFirstMatchingHandlerGetsInvoked() throws Exception {
        SwitchFilter filter = new SwitchFilter();

        // Build a chain where only the first matching case should be executed
        filter.onRequest.add(buildCase("${false}", handler1));
        filter.onRequest.add(buildCase("${true}", handler2)); // <- This one matches and is executed
        filter.onRequest.add(buildCase("${false}", handler3));
        filter.onRequest.add(buildCase("${true}", handler4));

        filter.filter(exchange, terminalHandler);

        // Ensure that only the first handler with true condition gets invoked
        // Other cases in the chain will never be tested
        verifyZeroInteractions(handler1, handler3, handler4);
        verify(handler2).handle(exchange);
    }

    private static SwitchFilter.Case buildCase(String expression, Handler handler) throws Exception {
        SwitchFilter.Case aCase = new SwitchFilter.Case();
        aCase.condition = new Expression(expression);
        aCase.handler = handler;
        return aCase;
    }
}
