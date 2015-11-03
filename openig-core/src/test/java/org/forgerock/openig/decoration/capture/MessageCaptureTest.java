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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MessageCaptureTest {

    @Spy
    private Logger logger = new Logger(null, Name.of("Test"));

    @Captor
    private ArgumentCaptor<String> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldLogRequest() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        capture.capture(new RootContext(), new Request(), CapturePoint.REQUEST);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogContext() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false, true);

        AttributesContext attributesContext = new AttributesContext(new RootContext());
        attributesContext.getAttributes().put("a", "b");
        capture.capture(attributesContext, new Request(), CapturePoint.REQUEST);

        verify(logger).info(captor.capture());
        assertThat(captor.getValue()).contains("\"a\": \"b\"");
    }

    @Test
    public void shouldLogFilteredRequest() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        capture.capture(new RootContext(), new Request(), CapturePoint.FILTERED_REQUEST);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogResponse() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        capture.capture(new RootContext(), new Response(), CapturePoint.RESPONSE);

        verify(logger).info(anyString());
    }

    @Test
    public void shouldLogFilteredResponse() throws Exception {
        MessageCapture capture = new MessageCapture(logger, false);

        capture.capture(new RootContext(), new Response(), CapturePoint.FILTERED_RESPONSE);

        verify(logger).info(anyString());
    }
}
