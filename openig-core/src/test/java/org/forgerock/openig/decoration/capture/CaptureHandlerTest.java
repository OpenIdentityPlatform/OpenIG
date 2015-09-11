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

package org.forgerock.openig.decoration.capture;

import static java.util.Arrays.asList;
import static org.forgerock.openig.decoration.capture.CapturePoint.FILTERED_REQUEST;
import static org.forgerock.openig.decoration.capture.CapturePoint.FILTERED_RESPONSE;
import static org.forgerock.openig.decoration.capture.CapturePoint.REQUEST;
import static org.forgerock.openig.decoration.capture.CapturePoint.RESPONSE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.TreeSet;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CaptureHandlerTest {

    @Mock
    private Handler delegate;

    @Mock
    private MessageCapture capture;
    private Response response;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        response = new Response();
        when(delegate.handle(any(Context.class), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));
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
        CaptureHandler handler = new CaptureHandler(delegate, capture, new TreeSet<>(points));

        Exchange exchange = new Exchange();
        handler.handle(exchange, null).get();

        for (CapturePoint capturePoint : points) {
            // A handler does not capture "filtered" messages, but should accept them as parameter, simply ignoring them
            switch (capturePoint) {
            case REQUEST:
                verify(capture).capture(exchange, (Request) null, capturePoint);
                break;
            case RESPONSE:
                verify(capture).capture(exchange, response, capturePoint);
                break;
            }
        }
        verifyNoMoreInteractions(capture);
    }

}
