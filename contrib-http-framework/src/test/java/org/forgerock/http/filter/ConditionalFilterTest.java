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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.http.filter;

import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConditionalFilterTest {

    private Context context;
    private Request request;

    @Mock
    private Filter delegate;

    @Mock
    private Handler next;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        context = new RootContext();
        request = new Request();
    }

    @Test
    public void shouldExecuteTheDelegatedFilter() throws Exception {
        new ConditionalFilter(delegate, true).filter(context, request, next);
        verify(delegate).filter(eq(context), eq(request), eq(next));
    }

    @Test
    public void shouldSkipTheDelegatedFilter() throws Exception {
        new ConditionalFilter(delegate, false).filter(context, request, next);
        verifyNoMoreInteractions(delegate);
        verify(next).handle(eq(context), eq(request));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldEvaluateTheConditionForEveryRequest() throws Exception {
        AsyncFunction mock = mock(AsyncFunction.class);
        when(mock.apply(any(ContextAndRequest.class))).thenReturn(newResultPromise(false), newResultPromise(true));
        Filter filter = new ConditionalFilter(delegate, mock);

        // First time the function evaluates to false
        filter.filter(context, request, next);

        verifyNoMoreInteractions(delegate);
        verify(next).handle(eq(context), eq(request));

        // Second time the function evaluates to true
        reset(delegate, next);
        filter.filter(context, request, next);

        verify(delegate).filter(eq(context), eq(request), eq(next));
        verifyNoMoreInteractions(next);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSkipTheDelegatedFilterInCaseOfException() throws Exception {
        AsyncFunction mock = mock(AsyncFunction.class);
        when(mock.apply(any(ContextAndRequest.class))).thenThrow(new Exception("Boom"));
        Filter filter = new ConditionalFilter(delegate, mock);

        filter.filter(context, request, next);

        verifyNoMoreInteractions(delegate);
        verify(next).handle(eq(context), eq(request));
    }
}
