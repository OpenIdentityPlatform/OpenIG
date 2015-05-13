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

package org.forgerock.openig.decoration.timer;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TimerFilterTest {

    private Filter delegate;

    @Mock
    private Handler terminal;

    @Spy
    private Logger logger = new Logger(new NullLogSink(), Name.of("Test"));

    @Spy
    private LogTimer timer = new LogTimer(logger);

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(timer).when(logger).getTimer();
        delegate = new DelegateFilter();
    }

    @Test
    public void shouldLogStartedAndElapsedMessages() throws Exception {
        TimerFilter time = new TimerFilter(delegate, logger);

        Exchange exchange = new Exchange();
        when(delegate.filter(exchange, null, terminal))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        time.filter(exchange, null, terminal).get();

        InOrder inOrder = inOrder(timer, terminal);
        inOrder.verify(timer).start();
        inOrder.verify(timer).pause();
        inOrder.verify(terminal).handle(exchange, null);
        inOrder.verify(timer).resume();
        inOrder.verify(timer).stop();
    }

    private static class DelegateFilter implements Filter {
        @Override
        public Promise<Response, NeverThrowsException> filter(final Context context,
                                                              final Request request,
                                                              final Handler next) {
            return next.handle(context, request);
        }
    }
}
