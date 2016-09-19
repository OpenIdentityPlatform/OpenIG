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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
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
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SequenceHandlerTest {

    private PromiseImpl<Response, NeverThrowsException> promise1;
    private PromiseImpl<Response, NeverThrowsException> promise2;

    @Mock
    private Handler handler1;

    @Mock
    private Handler handler2;

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
        Response response = new Response(Status.OK);
        promise1.handleResult(response);

        Context context = new RootContext();
        Request request = new Request();
        Promise<Response, NeverThrowsException> result = sequence.handle(context, request);
        assertThat(result.get()).isSameAs(response);
    }

    @Test
    public void shouldExecuteMultiElementSequenceCompletely() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, null);
        sequence.addBinding(handler2, null);
        Response response1 = new Response(Status.OK);
        promise1.handleResult(response1);
        Response response2 = new Response(Status.OK);
        promise2.handleResult(response2);

        Context context = new RootContext();
        Request request = new Request();
        Promise<Response, NeverThrowsException> result = sequence.handle(context, request);
        assertThat(result.get()).isSameAs(response2);
    }

    @Test
    public void shouldExecuteMultiElementSequencePartially() throws Exception {
        SequenceHandler sequence = new SequenceHandler();
        sequence.addBinding(handler1, Expression.valueOf("${false}", Boolean.class));
        sequence.addBinding(handler2, null);
        Response response1 = new Response(Status.OK);
        promise1.handleResult(response1);
        Response response2 = new Response(Status.OK);
        promise2.handleResult(response2);

        Context context = new RootContext();
        Request request = new Request();
        Promise<Response, NeverThrowsException> result = sequence.handle(context, request);
        assertThat(result.get()).isSameAs(response1);
        verifyZeroInteractions(handler2);
    }
}
