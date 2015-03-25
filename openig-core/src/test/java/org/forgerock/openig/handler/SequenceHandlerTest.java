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

package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.forgerock.http.Context;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SequenceHandlerTest {

    private PromiseImpl<Response, ResponseException> promise1;
    private PromiseImpl<Response, ResponseException> promise2;

    @Mock
    private org.forgerock.http.Handler handler1;

    @Mock
    private org.forgerock.http.Handler handler2;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        promise1 = PromiseImpl.create();
        promise2 = PromiseImpl.create();
        when(handler1.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise1);
        when(handler2.handle(any(Context.class), any(Request.class)))
                .thenReturn(promise2);
    }

    @Test
    public void shouldExecuteSingleElementSequenceCompletely() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, null);
        Response response = new Response();
        promise1.handleResult(response);

        Exchange exchange = new Exchange();
        Request request = new Request();
        Promise<Response, ResponseException> result = sequence.handle(exchange, request);
        assertThat(result.get()).isSameAs(response);
    }

    @Test
    public void shouldExecuteMultiElementSequenceCompletely() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, null);
        sequence.addBinding(handler2, null);
        Response response1 = new Response();
        promise1.handleResult(response1);
        Response response2 = new Response();
        promise2.handleResult(response2);

        Exchange exchange = new Exchange();
        Request request = new Request();
        Promise<Response, ResponseException> result = sequence.handle(exchange, request);
        assertThat(result.get()).isSameAs(response2);
    }

    @Test
    public void shouldExecuteMultiElementSequencePartially() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, Expression.valueOf("${false}"));
        sequence.addBinding(handler2, null);
        Response response1 = new Response();
        promise1.handleResult(response1);
        Response response2 = new Response();
        promise2.handleResult(response2);

        Exchange exchange = new Exchange();
        Request request = new Request();
        Promise<Response, ResponseException> result = sequence.handle(exchange, request);
        assertThat(result.get()).isSameAs(response1);
        verifyZeroInteractions(handler2);
    }

    @Test
    public void shouldReturnFailedPromise() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, null);
        sequence.addBinding(handler2, null);
        Response response1 = new Response();
        promise1.handleResult(response1);
        ResponseException error = new ResponseException(404, "Boom");
        promise2.handleError(error);

        Exchange exchange = new Exchange();
        Request request = new Request();
        Promise<Response, ResponseException> result = sequence.handle(exchange, request);
        try {
            result.getOrThrow();
            failBecauseExceptionWasNotThrown(ResponseException.class);
        } catch (ResponseException re) {
            assertThat(re).hasMessage("Boom");
            assertThat(re.getResponse().getStatus()).isEqualTo(404);
        }
        verify(handler1).handle(exchange, request);
    }
}
