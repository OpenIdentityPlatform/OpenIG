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
 * Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

/**
 * Tests the Handlers#chain method.
 */
public final class HandlersTest {

    private static final Response RESPONSE = new Response();

    @Test
    public void testFilterCanInvokeAllFiltersAndHandler() throws ResponseException {
        Handler target = target();
        Filter filter1 = mock(Filter.class);
        doAnswer(invoke()).when(filter1).filter(any(Context.class), any(Request.class), any(Handler.class));
        Filter filter2 = filter();
        Handler chain = Http.chainOf(target, filter1, filter2);
        Context context = context();
        Request request = new Request();

        // The handler will be invoked twice which is obviously unrealistic. In practice,
        // filter1 will wrap the result handler and combine/reduce the result of the two
        // sub-reads to produce a single read response. We won't do that here in order
        // to keep the test simple.
        chain.handle(context, request);

        InOrder inOrder = inOrder(filter1, filter2, target);
        inOrder.verify(filter1).filter(same(context), same(request), any(Handler.class));
        inOrder.verify(filter2).filter(same(context), same(request), any(Handler.class));
        inOrder.verify(target).handle(context, request);
    }

    /**
     * Tests that a filter can call next filter multiple times. If the cursoring
     * mechanism increments the index for each call then the first invocation
     * will iterate to the target causing the second invocation to go direct to
     * the target rather than the next filter.
     */
    @Test
    public void testFilterCanInvokeMultipleSubRequests() throws ResponseException {
        Handler target = target();
        Filter filter1 = mock(Filter.class);
        doAnswer(invoke(2)).when(filter1).filter(any(Context.class), any(Request.class), any(Handler.class));
        Filter filter2 = filter();
        Handler chain = Http.chainOf(target, filter1, filter2);
        Context context = context();
        Request request = new Request();

        // The handler will be invoked twice which is obviously unrealistic. In practice,
        // filter1 will wrap the result handler and combine/reduce the result of the two
        // sub-reads to produce a single read response. We won't do that here in order
        // to keep the test simple.
        chain.handle(context, request);

        InOrder inOrder = inOrder(filter1, filter2, target, target);
        inOrder.verify(filter1).filter(same(context), same(request), any(Handler.class));
        // First read of next filter
        inOrder.verify(filter2).filter(same(context), same(request), any(Handler.class));
        inOrder.verify(target).handle(context, request);

        // Second read of next filter
        inOrder.verify(filter2).filter(same(context), same(request), any(Handler.class));
        inOrder.verify(target).handle(context, request);
    }

    @Test
    public void testFilterCanStopProcessingWithError() throws ResponseException {
        Handler target = target();
        Filter filter1 = mock(Filter.class);
        doAnswer(new Answer<Promise<Response, ResponseException>>() {
            @Override
            public Promise<Response, ResponseException> answer(final InvocationOnMock invocation) throws Throwable {
                return Promises.newFailedPromise(new ResponseException(RESPONSE));
            }
        }).when(filter1).filter(any(Context.class), any(Request.class), any(Handler.class));
        Filter filter2 = filter();
        Handler chain = Http.chainOf(target, filter1, filter2);
        Context context = context();
        Request request = new Request();

        Response response = null;
        try {
            chain.handle(context, request).getOrThrowUninterruptibly();
        } catch (ResponseException error) {
            response = error.getResponse();
        }

        InOrder inOrder = inOrder(filter1, filter2, target, target);
        inOrder.verify(filter1).filter(same(context), same(request), any(Handler.class));
        assertThat(response).isEqualTo(RESPONSE);
        verifyZeroInteractions(filter2, target);
    }

    @Test
    public void testFilterCanStopProcessingWithResult() throws ResponseException {
        Handler target = target();
        Filter filter1 = mock(Filter.class);
        doAnswer(new Answer<Promise<Response, ResponseException>>() {
            @Override
            public Promise<Response, ResponseException> answer(final InvocationOnMock invocation) throws Throwable {
                return Promises.newSuccessfulPromise(RESPONSE);
            }
        }).when(filter1).filter(any(Context.class), any(Request.class), any(Handler.class));
        Filter filter2 = filter();
        Handler chain = Http.chainOf(target, filter1, filter2);
        Context context = context();
        Request request = new Request();

        Response response = chain.handle(context, request).getOrThrowUninterruptibly();

        InOrder inOrder = inOrder(filter1, filter2, target, target);
        inOrder.verify(filter1).filter(same(context), same(request), any(Handler.class));
        assertThat(response).isEqualTo(RESPONSE);
        verifyZeroInteractions(filter2, target);
    }

    private Context context() {
        Session session = mock(Session.class);
        return new HttpContext(new RootContext(), session);
    }

    private Filter filter() throws ResponseException {
        Filter filter = mock(Filter.class);
        doAnswer(invoke()).when(filter).filter(any(Context.class), any(Request.class), any(Handler.class));
        return filter;
    }

    private Answer<Promise<Response, ResponseException>> invoke() {
        return invoke(1);
    }

    private Answer<Promise<Response, ResponseException>> invoke(final int count) {
        return new Answer<Promise<Response, ResponseException>>() {
            @Override
            public Promise<Response, ResponseException> answer(final InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                final Context context = (Context) args[0];
                final Request request = (Request) args[1];
                final Handler next = (Handler) args[2];
                Promise<Response, ResponseException> promise = Promises.newSuccessfulPromise(new Response());
                for (int i = 0; i < count; i++) {
                    promise.thenAsync(
                            new AsyncFunction<Response, Response, ResponseException>() {
                                @Override
                                public Promise<Response, ResponseException> apply(Response o) throws ResponseException {
                                    return next.handle(context, request);
                                }
                            }
                    );
                }
                return promise;
            }
        };
    }

    private Answer<Response> result() {
        return new Answer<Response>() {
            @Override
            public Response answer(final InvocationOnMock invocation) throws Throwable {
                return RESPONSE;
            }
        };
    }

    private Handler target() throws ResponseException {
        Handler target = mock(Handler.class);
        doAnswer(result()).when(target).handle(any(Context.class), any(Request.class));
        return target;
    }
}
