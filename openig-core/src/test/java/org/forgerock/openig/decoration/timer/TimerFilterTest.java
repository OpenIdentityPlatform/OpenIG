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

package org.forgerock.openig.decoration.timer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
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
        time.filter(exchange, terminal);

        InOrder inOrder = inOrder(timer, terminal);
        inOrder.verify(timer).start();
        inOrder.verify(timer).pause();
        inOrder.verify(terminal).handle(exchange);
        inOrder.verify(timer).resume();
        inOrder.verify(timer).stop();
    }

    @Test
    public void shouldLogStartedAndElapsedMessagesWhenDelegateFilterIsFailing() throws Exception {
        TimerFilter time = new TimerFilter(delegate, logger);
        Exchange exchange = new Exchange();

        doThrow(HandlerException.class).when(terminal).handle(exchange);

        try {
            time.filter(exchange, terminal);
            failBecauseExceptionWasNotThrown(HandlerException.class);
        } catch (Exception e) {
            InOrder inOrder = inOrder(timer);
            inOrder.verify(timer).start();
            inOrder.verify(timer).pause();
            inOrder.verify(timer).resume();
            inOrder.verify(timer).stop();
        }
    }

    private static class DelegateFilter implements Filter {
        @Override
        public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
            next.handle(exchange);
        }
    }
}
