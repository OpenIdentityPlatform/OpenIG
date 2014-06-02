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

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.BranchingInputStream;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

public class ExceptionFilterTest {

    @Mock
    private Handler nextHandler;

    @Mock
    private Handler exceptionHandler;

    @Mock
    private BranchingInputStream entity;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExceptionHandlerNotInvokedWhenNoExceptionIsThrown() throws Exception {
        ExceptionFilter filter = new ExceptionFilter();
        filter.handler = exceptionHandler;

        filter.filter(null, nextHandler);

        verify(nextHandler).handle(null);
        verifyZeroInteractions(exceptionHandler);
    }

    @Test
    public void testExceptionHandlerIsInvokedAndResponseEntityIsClosedWhenExceptionIsThrown() throws Exception {
        Exchange exchange = new Exchange();
        exchange.response = new Response();
        exchange.response.entity = entity;

        doThrow(new HandlerException("Boom")).when(nextHandler).handle(exchange);

        ExceptionFilter filter = new ExceptionFilter();
        filter.handler = exceptionHandler;

        filter.filter(exchange, nextHandler);

        verify(exceptionHandler).handle(exchange);
        verify(entity).close();
    }
}
