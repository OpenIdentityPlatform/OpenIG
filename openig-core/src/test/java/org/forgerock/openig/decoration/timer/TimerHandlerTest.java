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

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TimerHandlerTest {

    @Mock
    private Handler delegate;

    @Spy
    private Logger logger = new Logger(new NullLogSink(), Name.of("Test"));

    @Spy
    private LogTimer timer = new LogTimer(logger);

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(timer).when(logger).getTimer();
    }

    @Test
    public void shouldLogStartedAndElapsedMessages() throws Exception {
        TimerHandler time = new TimerHandler(delegate, logger);

        Context context = new RootContext();
        when(delegate.handle(context, null))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));
        time.handle(context, null).get();

        InOrder inOrder = inOrder(timer, delegate);
        inOrder.verify(timer).start();
        inOrder.verify(delegate).handle(context, null);
        inOrder.verify(timer).stop();
    }

}
