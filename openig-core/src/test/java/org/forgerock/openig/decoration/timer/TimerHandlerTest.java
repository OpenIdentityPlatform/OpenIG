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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.timer;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.guava.common.base.Ticker;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.heap.Name;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TimerHandlerTest {

    private Logger logger;

    @Mock
    private Handler delegate;

    @Mock
    private org.forgerock.openig.decoration.Context context;

    @Mock
    private Ticker ticker;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        when(context.getName()).thenReturn(Name.of("myDecoratedHandler"));
        logger = LoggerFactory.getLogger("decoratedObjectName");
    }

    @Test
    public void shouldReadTickerForAllInterceptions() throws Exception {
        // Given
        TimerHandler handler = new TimerHandler(delegate, logger, ticker, MICROSECONDS);
        when(delegate.handle(null, null)).thenReturn(newResponsePromise(new Response(Status.OK)));
        // When
        handler.handle(null, null).get();
        // Then
        verify(delegate).handle(null, null);
        verify(ticker, times(2)).read();
    }

}
