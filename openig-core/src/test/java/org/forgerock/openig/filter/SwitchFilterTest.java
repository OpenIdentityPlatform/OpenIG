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

package org.forgerock.openig.filter;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SwitchFilterTest {

    private Context context = new RootContext();

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
        when(terminalHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        when(handler1.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        when(handler2.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        when(handler3.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        when(handler4.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
    }

    @Test
    public void testSwitchOnRequestOnly() throws Exception {
        SwitchFilter filter = new SwitchFilter();
        filter.addRequestCase(Expression.valueOf("${true}", Boolean.class), handler1);

        filter.filter(context, null, terminalHandler);

        // Filter's handler should not be called because the stream has been diverted
        verifyZeroInteractions(terminalHandler);
        verify(handler1).handle(context, null);
    }

    @Test
    public void testSwitchOnResponseOnly() throws Exception {
        SwitchFilter filter = new SwitchFilter();
        filter.addResponseCase(Expression.valueOf("${true}", Boolean.class), handler1);

        filter.filter(context, null, terminalHandler);

        // No request conditions were met (there was none), so the responses cases are tried
        // and the case's handler that evaluated to true is being executed
        InOrder inOrder = inOrder(terminalHandler, handler1);
        inOrder.verify(terminalHandler).handle(context, null);
        inOrder.verify(handler1).handle(context, null);
    }

    @Test
    public void testSwitchWithConditionOnResponseStatus() throws Exception {
        SwitchFilter filter = new SwitchFilter();
        filter.addResponseCase(Expression.valueOf("${response.status.code == 418}", Boolean.class), handler1);

        // Reset the terminalHandler as we want another result here.
        Mockito.reset(terminalHandler);
        when(terminalHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response(Status.TEAPOT)));

        filter.filter(context, null, terminalHandler);

        // No request conditions were met (there was none), so the responses cases are tried
        // and the case's handler that evaluated to true is being executed
        InOrder inOrder = inOrder(terminalHandler, handler1);
        inOrder.verify(terminalHandler).handle(context, null);
        inOrder.verify(handler1).handle(context, null);
    }

    @Test
    public void testResponseHandlerNotInvokedIfRequestHasBeenDiverted() throws Exception {
        SwitchFilter filter = new SwitchFilter();

        // Expect the request's case to divert the flow and ignore the response's case
        filter.addRequestCase(Expression.valueOf("${true}", Boolean.class), handler1);
        filter.addResponseCase(Expression.valueOf("${true}", Boolean.class), handler2);

        filter.filter(context, null, terminalHandler);

        // As the request condition is fulfilled, the handler plugged onto
        // the response should not be called
        verify(handler1).handle(context, null);
        verifyZeroInteractions(terminalHandler);
        verifyZeroInteractions(handler2);
    }

    @Test
    public void testThatOnlyFirstMatchingHandlerGetsInvoked() throws Exception {
        SwitchFilter filter = new SwitchFilter();

        // Build a chain where only the first matching case should be executed
        filter.addRequestCase(Expression.valueOf("${false}", Boolean.class), handler1);
        // The following one matches and is executed
        filter.addRequestCase(Expression.valueOf("${true}", Boolean.class), handler2);
        filter.addRequestCase(Expression.valueOf("${false}", Boolean.class), handler3);
        filter.addRequestCase(Expression.valueOf("${true}", Boolean.class), handler4);

        filter.filter(context, null, terminalHandler);

        // Ensure that only the first handler with true condition gets invoked
        // Other cases in the chain will never be tested
        verifyZeroInteractions(handler1, handler3, handler4);
        verify(handler2).handle(context, null);
    }
}
