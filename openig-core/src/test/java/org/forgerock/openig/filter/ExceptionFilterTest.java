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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ExceptionFilterTest {

    @Mock
    private Handler nextHandler;

    @Mock
    private Handler exceptionHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExceptionHandlerNotInvokedWhenNoExceptionIsThrown() throws Exception {

        Response response = new Response();
        when(nextHandler.handle(null, null))
                .thenReturn(Promises.<Response, ResponseException>newResultPromise(response));

        ExceptionFilter filter = new ExceptionFilter(exceptionHandler);

        assertThat(filter.filter(null, null, nextHandler).getOrThrowUninterruptibly())
                .isEqualTo(response);

        verifyZeroInteractions(exceptionHandler);
    }

    @Test
    public void testExceptionHandlerIsInvokedWhenFiledPromiseIsReturned() throws Exception {

        Response response = new Response();
        when(exceptionHandler.handle(null, null))
                .thenReturn(Promises.<Response, ResponseException>newResultPromise(response));
        ResponseException exception = new ResponseException(Status.INTERNAL_SERVER_ERROR);
        when(nextHandler.handle(null, null))
                .thenReturn(Promises.<Response, ResponseException>newExceptionPromise(exception));

        ExceptionFilter filter = new ExceptionFilter(exceptionHandler);

        // this shouldn't throw any exception
        assertThat(filter.filter(null, null, nextHandler).getOrThrowUninterruptibly())
                .isEqualTo(response);
    }
}
