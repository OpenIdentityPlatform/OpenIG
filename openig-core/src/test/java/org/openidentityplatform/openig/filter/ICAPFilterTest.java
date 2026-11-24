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
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.http.io.IO.newBranchingInputStream;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import net.rfc3507.client.ICAPClient;
import net.rfc3507.client.ICAPRequest;
import net.rfc3507.client.ICAPResponse;

@SuppressWarnings("javadoc")
public class ICAPFilterTest {

    @Test
    public void shouldConfigureTimeouts() throws Exception {
        ICAPFilter filter = (ICAPFilter) new ICAPFilter.Heaplet().create(
                Name.of("icap"),
                json(object(field("server", "icap://localhost:1344"),
                            field("connect_timeout", "1000"),
                            field("read_timeout", "2000"))),
                HeapUtilsTest.buildDefaultHeap());

        assertThat(filter.icap.getConnectTimeout()).isEqualTo(1000);
        assertThat(filter.icap.getReadTimeout()).isEqualTo(2000);
    }

    @Test
    public void shouldApplyDefaultTimeouts() throws Exception {
        ICAPFilter filter = (ICAPFilter) new ICAPFilter.Heaplet().create(
                Name.of("icap"),
                json(object(field("server", "icap://localhost:1344"))),
                HeapUtilsTest.buildDefaultHeap());

        assertThat(filter.icap.getConnectTimeout()).isEqualTo(5000);
        assertThat(filter.icap.getReadTimeout()).isEqualTo(15000);
    }

    @DataProvider
    public static Object[][] timeoutFields() {
        return new Object[][] { { "connect_timeout" }, { "read_timeout" } };
    }

    @Test(dataProvider = "timeoutFields")
    public void shouldRejectNonNumericTimeoutNamingTheField(String name) {
        assertThatThrownBy(() -> new ICAPFilter.Heaplet().create(
                Name.of("icap"),
                json(object(field("server", "icap://localhost:1344"),
                            field(name, "soon"))),
                HeapUtilsTest.buildDefaultHeap()))
                .isInstanceOf(JsonValueException.class)
                .hasMessageContaining(name)
                .hasMessageContaining("soon");
    }
    @DataProvider
    public static Object[][] requestBodyReadFailurePositions() {
        return new Object[][] { { 0 }, { 1 } };
    }

    @Test(dataProvider = "requestBodyReadFailurePositions")
    public void shouldRejectUnreadableRequestBody(int readableBytes) throws Exception {
        ICAPFilter filter = new ICAPFilter();
        filter.icap = mock(ICAPClient.class);
        Handler next = (context, request) -> newResultPromise(new Response(Status.OK));
        InputStream body = new InputStream() {
            private int remaining = readableBytes;

            @Override
            public int read() throws IOException {
                if (remaining == 0) {
                    throw new IOException("Request body read failed");
                }
                remaining--;
                return 'x';
            }
        };

        try (Request request = new Request().setEntity(newBranchingInputStream(body, newTemporaryStorage()));
             Response response = filter.filter(new RootContext(), request, next).get()) {
            assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
            verifyNoInteractions(filter.icap);
        }
    }

    @Test
    public void shouldForwardEmptyRequestWithoutCallingIcap() throws Exception {
        ICAPFilter filter = new ICAPFilter();
        filter.icap = mock(ICAPClient.class);
        Handler next = (context, request) -> newResultPromise(new Response(Status.OK));

        try (Request request = new Request();
             Response response = filter.filter(new RootContext(), request, next).get()) {
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            verifyNoInteractions(filter.icap);
        }
    }

    @Test
    public void shouldSendFullRequestBodyToIcap() throws Exception {
        ICAPFilter filter = new ICAPFilter();
        filter.icap = mock(ICAPClient.class);
        ICAPResponse icapResponse = mock(ICAPResponse.class);
        when(icapResponse.getStatus()).thenReturn(204);
        when(filter.icap.execute(any(ICAPRequest.class))).thenReturn(icapResponse);
        Handler next = (context, request) -> newResultPromise(new Response(Status.OK));
        byte[] body = { 1, 2, 3 };
        InputStream source = new ByteArrayInputStream(body);

        try (Request request = new Request().setEntity(newBranchingInputStream(source, newTemporaryStorage()));
             Response response = filter.filter(new RootContext(), request, next).get()) {
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            ArgumentCaptor<ICAPRequest> sentRequest = ArgumentCaptor.forClass(ICAPRequest.class);
            verify(filter.icap).execute(sentRequest.capture());
            assertThat(sentRequest.getValue().getHttpRequestBody()).containsExactly(body);
        }
    }

}
