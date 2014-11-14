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

import static org.mockito.Mockito.*;

import org.forgerock.openig.http.Exchange;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.openig.log.Logger;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MessageCaptureTest {

    @Spy
    private Logger logger = new Logger(null, "Test");

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldLogRequest() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        capture.capture(exchange, CapturePoint.REQUEST);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogFilteredRequest() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        capture.capture(exchange, CapturePoint.FILTERED_REQUEST);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogResponse() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        exchange.response = new Response();
        capture.capture(exchange, CapturePoint.RESPONSE);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogFilteredResponse() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        exchange.response = new Response();
        capture.capture(exchange, CapturePoint.FILTERED_RESPONSE);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldSupportMissingRequest() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        capture.capture(exchange, CapturePoint.REQUEST);
        capture.capture(exchange, CapturePoint.FILTERED_REQUEST);

        verify(logger, times(2)).info(anyString());
    }

    @Test
    public void shouldSupportMissingResponse() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        Exchange exchange = new Exchange();
        capture.capture(exchange, CapturePoint.RESPONSE);
        capture.capture(exchange, CapturePoint.FILTERED_RESPONSE);

        verify(logger, times(2)).info(anyString());
    }
}
