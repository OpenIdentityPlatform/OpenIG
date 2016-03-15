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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.services.TransactionId;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.TransactionIdContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class NullResponseFilterTest {

    private static final String EXAMPLE_URI = "http://www.example.com/";
    private static final String FAKE_TRANSACTION_ID = "fakeTransactionId";

    private Context context;
    private ResponseHandler next;
    private Request request;

    @Captor
    private ArgumentCaptor<String> logMessage;

    @Spy
    private Logger logger = new Logger(new NullLogSink(), Name.of("logger"));

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        request = new Request();
        request.setUri(EXAMPLE_URI);
        context = new RootContext();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullLogger() {
        new NullResponseFilter(null);
    }

    @Test
    public void shouldLogAMessageWhenResponseIsNull() throws Exception {
        next = new ResponseHandler((Response) null);
        new NullResponseFilter(logger).filter(context, request, next);

        verify(logger).debug(logMessage.capture());
        assertThat(logMessage.getValue()).contains(EXAMPLE_URI, context.getId());
    }

    @Test
    public void shouldLogAMessageWhenResponseIsNullAndContextContainsATransactionId() throws Exception {
        context = new TransactionIdContext(new RootContext(),
                                           TransactionId.valueOf(json(object(field("value", FAKE_TRANSACTION_ID),
                                                                             field("subTransactionIdCounter", 1)))));
        next = new ResponseHandler((Response) null);
        new NullResponseFilter(logger).filter(context, request, next);

        verify(logger).debug(logMessage.capture());
        assertThat(logMessage.getValue()).contains(EXAMPLE_URI, FAKE_TRANSACTION_ID);
    }

    @Test
    public void shouldNotLogAMessageWhenResponseIsNotNull() throws Exception {
        next = new ResponseHandler(new Response(OK));
        new NullResponseFilter(logger).filter(context, request, next);

        verifyZeroInteractions(logger);
    }
}
