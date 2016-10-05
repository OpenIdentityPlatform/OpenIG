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

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.services.context.ClientContext.buildExternalClientContext;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.forgerock.http.Handler;
import org.forgerock.http.filter.ResponseHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.services.TransactionId;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RequestAuditContext;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.TransactionIdContext;
import org.forgerock.util.time.TimeService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class HttpAccessAuditFilterTest {

    private Request request;

    @Mock
    private TimeService time;

    @Mock
    private RequestHandler reqHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        request = new Request();
        request.setMethod("GET");
        request.setUri("https://www.example.com/rockstar?who=forgerock");

        when(reqHandler.handleCreate(any(Context.class), any(CreateRequest.class)))
                .thenReturn(newResourceResponse("1", "1", json(object())).asPromise());

        when(time.now()).thenReturn(1L, 2L, 5L).thenThrow(new RuntimeException("too many calls"));
    }

    @Test
    public void shouldSendAnAccessEvent() throws Exception {
        HttpAccessAuditFilter filter = new HttpAccessAuditFilter(reqHandler, time);

        Handler chfHandler = new ResponseHandler(Status.OK);

        final Response response = filter.filter(context(), request, chfHandler).get();

        assertThat(response.getStatus()).isSameAs(Status.OK);
        verifyAuditServiceCall(reqHandler, response.getStatus());
    }

    @Test
    public void shouldNotSendAnAccessEventOnNullResponse() throws Exception {
        HttpAccessAuditFilter filter = new HttpAccessAuditFilter(reqHandler, time);

        filter.filter(context(),
                      request,
                      new ResponseHandler((Response) null)).get();

        verifyZeroInteractions(reqHandler);
    }

    private void verifyAuditServiceCall(RequestHandler handler, Status status) {
        ArgumentCaptor<CreateRequest> createRequestCaptor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(handler).handleCreate(any(Context.class), createRequestCaptor.capture());

        final JsonValue content = createRequestCaptor.getValue().getContent();
        assertThat(content.get("eventName").asString()).isEqualTo("OPENIG-HTTP-ACCESS");
        assertThat(content.get("timestamp").asString()).isEqualTo("1970-01-01T00:00:00.002Z");
        assertThat(content.get("transactionId").asString()).isEqualTo("txId");
        assertThat(content.get("response").get("elapsedTime").asLong()).isEqualTo(4L);
        assertThat(content.get("response").get("elapsedTimeUnits").asString()).isEqualTo("MILLISECONDS");
        assertThat(content.get("response").get("statusCode").asString()).isEqualTo(String.valueOf(status.getCode()));
        assertThat(content.get("server").get("ip").asString()).isEqualTo("127.0.0.1");
        assertThat(content.get("server").get("port").asInteger()).isEqualTo(80);
        assertThat(content.get("http").get("request").get("method").asString()).isEqualTo("GET");
        assertThat(content.get("http").get("request").get("path").asString())
                .isEqualTo("https://www.example.com/rockstar");
        assertThat(content.get("http").get("request").get("queryParameters").asMapOfList(String.class))
                .contains(entry("who", Collections.singletonList("forgerock")));
    }

    private Context context() {
        Context context = new RootContext();
        context = new TransactionIdContext(context, new TransactionId("txId"));
        context = buildExternalClientContext(context)
                .localAddress("127.0.0.1")
                .localPort(80)
                .build();
        context = new RequestAuditContext(context, time);
        return context;
    }
}
