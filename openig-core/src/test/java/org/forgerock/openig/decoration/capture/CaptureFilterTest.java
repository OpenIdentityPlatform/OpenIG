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

package org.forgerock.openig.decoration.capture;

import static java.util.Arrays.*;
import static org.forgerock.openig.decoration.capture.CapturePoint.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.Logger;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CaptureFilterTest {

    private Filter delegate = new Filter() {
        @Override
        public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
            next.handle(exchange);
        }
    };

    @Mock
    private Handler terminal;

    @Spy
    private MessageCapture capture = new MessageCapture(new Logger(null, Name.of("Test")), false);

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @DataProvider
    public static Object[][] capturePointSets() {
        // @Checkstyle:off
        return new Object[][] {
                {asList(REQUEST)},
                {asList(RESPONSE)},
                {asList(FILTERED_REQUEST)},
                {asList(FILTERED_RESPONSE)},

                {asList(REQUEST, RESPONSE)},
                {asList(FILTERED_REQUEST, FILTERED_RESPONSE)},
                {asList(REQUEST, FILTERED_REQUEST)},
                {asList(RESPONSE, FILTERED_RESPONSE)},
                {asList(REQUEST, FILTERED_RESPONSE)},
                {asList(RESPONSE, FILTERED_REQUEST)},

                {asList(REQUEST, RESPONSE, FILTERED_REQUEST)},
                {asList(REQUEST, RESPONSE, FILTERED_RESPONSE)},
                {asList(FILTERED_REQUEST, FILTERED_RESPONSE, REQUEST)},
                {asList(FILTERED_REQUEST, FILTERED_RESPONSE, RESPONSE)},

                {asList(REQUEST, RESPONSE, FILTERED_REQUEST, FILTERED_RESPONSE)}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "capturePointSets")
    public void shouldCaptureAllMessages(List<CapturePoint> points) throws Exception {
        CaptureFilter filter = new CaptureFilter(delegate, capture, new TreeSet<CapturePoint>(points));

        Exchange exchange = new Exchange();
        filter.filter(exchange, terminal);

        verify(terminal).handle(exchange);
        for (CapturePoint capturePoint : points) {
            verify(capture).capture(exchange, capturePoint);
        }
        verifyNoMoreInteractions(capture);
    }

}
