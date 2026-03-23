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
 * Copyright 2026 3A Systems LLC.
 */

package org.forgerock.openig.filter;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.ValidationReport;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpenApiValidationFilterTest {

    private OpenApiInteractionValidator mockValidator;
    private Handler mockNextHandler;
    private Context rootContext;

    @BeforeMethod
    public void setUp() throws IOException {
        mockValidator   = mock(OpenApiInteractionValidator.class);
        mockNextHandler = mock(Handler.class);
        rootContext     = new RootContext();
    }

    @Test
    public void filter_forwardsRequest_whenRequestAndResponseAreValid() throws Exception {
        when(mockValidator.validateRequest(any())).thenReturn(ValidationReport.empty());
        when(mockValidator.validateResponse(any(), any(), any())).thenReturn(ValidationReport.empty());

        final Response upstreamResponse = new Response(Status.OK);
        upstreamResponse.setEntity("hello");
        when(mockNextHandler.handle(any(), any()))
                .thenReturn(Promises.newResultPromise(upstreamResponse));

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, false);
        final Response response = filter.filter(rootContext, buildGetRequest("http://localhost/pets"),
                mockNextHandler).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).isEqualTo("hello");
    }

    @Test
    public void filter_returns400_whenRequestValidationFails() throws Exception {
        when(mockValidator.validateRequest(any()))
                .thenReturn(singleErrorReport("Request body is required"));

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, false);
        final Response response = filter.filter(rootContext, buildGetRequest("http://localhost/pets"),
                mockNextHandler).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        assertThat(response.getEntity().getString()).contains("Request validation failed");
        assertThat(response.getEntity().getString()).contains("Request body is required");

        verify(mockNextHandler, never()).handle(any(), any());
    }

    @Test
    public void filter_passesResponse_whenResponseValidationFailsAndFlagIsFalse() throws Exception {
        when(mockValidator.validateRequest(any())).thenReturn(ValidationReport.empty());
        when(mockValidator.validateResponse(any(), any(), any()))
                .thenReturn(singleErrorReport("Response missing required field"));

        final Response upstreamResponse = new Response(Status.OK);
        upstreamResponse.setEntity("{\"x\":1}");
        when(mockNextHandler.handle(any(), any()))
                .thenReturn(Promises.newResultPromise(upstreamResponse));

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, false);
        final Response response = filter.filter(rootContext, buildGetRequest("http://localhost/pets"),
                mockNextHandler).get();

        // Response passes through despite the validation error (log-only mode)
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void filter_returns503_whenResponseValidationFailsAndFlagIsTrue() throws Exception {
        when(mockValidator.validateRequest(any())).thenReturn(ValidationReport.empty());
        when(mockValidator.validateResponse(any(), any(), any()))
                .thenReturn(singleErrorReport("Response schema mismatch"));

        final Response upstreamResponse = new Response(Status.OK);
        upstreamResponse.setEntity("{\"x\":1}");
        when(mockNextHandler.handle(any(), any()))
                .thenReturn(Promises.newResultPromise(upstreamResponse));

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, true);
        final Response response = filter.filter(rootContext, buildGetRequest("http://localhost/pets"),
                mockNextHandler).get();

        assertThat(response.getStatus()).isEqualTo(Status.SERVICE_UNAVAILABLE);
        assertThat(response.getEntity().getString()).contains("Response validation failed");
        assertThat(response.getEntity().getString()).contains("Response schema mismatch");
    }

    @Test
    public void filter_preservesRequestBodyForDownstream() throws Exception {
        when(mockValidator.validateRequest(any())).thenReturn(ValidationReport.empty());
        when(mockValidator.validateResponse(any(), any(), any())).thenReturn(ValidationReport.empty());

        final String requestBody = "{\"name\":\"Fido\"}";
        AtomicReference<String> capturedBody = new AtomicReference<>();

        when(mockNextHandler.handle(any(), any())).thenAnswer(inv -> {
            final Request req = inv.getArgument(1);
            capturedBody.set(req.getEntity().getString());
            final Response r = new Response(Status.CREATED);
            r.setEntity("{}");
            return Promises.newResultPromise(r);
        });

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, false);
        filter.filter(rootContext, buildPostRequest("http://localhost/pets", requestBody),
                mockNextHandler).get();

        assertThat(capturedBody.get()).isEqualTo(requestBody);
    }

    @Test
    public void filter_preservesResponseBodyForCaller() throws Exception {
        when(mockValidator.validateRequest(any())).thenReturn(ValidationReport.empty());
        when(mockValidator.validateResponse(any(), any(), any())).thenReturn(ValidationReport.empty());

        final String body = "{\"id\":42,\"name\":\"Fido\"}";
        final Response upstreamResponse = new Response(Status.OK);
        upstreamResponse.setEntity(body);
        when(mockNextHandler.handle(any(), any()))
                .thenReturn(Promises.newResultPromise(upstreamResponse));

        final OpenApiValidationFilter filter = new OpenApiValidationFilter(mockValidator, false);
        final Response response = filter.filter(rootContext,
                buildGetRequest("http://localhost/pets/42"), mockNextHandler).get();

        assertThat(response.getEntity().getString()).isEqualTo(body);
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void heaplet_throwsHeapException_whenSpecFileDoesNotExist() throws Exception {
        final org.forgerock.openig.heap.HeapImpl heap = new HeapImpl(Name.of("test"));
        final JsonValue config = json(
                org.forgerock.json.JsonValue.object(
                        org.forgerock.json.JsonValue.field("spec",
                                "${read('/nonexistent/path/spec.yaml')}")));

        new OpenApiValidationFilter.Heaplet().create(Name.of("testFilter"), config, heap);
    }

    @Test
    public void heaplet_createsFilter_withValidSpecFile() throws Exception {
        final String spec =
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Test\n"
                        + "  version: '1'\n"
                        + "paths:\n"
                        + "  /items:\n"
                        + "    get:\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n";

        final org.forgerock.openig.heap.HeapImpl heap = new HeapImpl(Name.of("test"));
        final org.forgerock.json.JsonValue config = org.forgerock.json.JsonValue.json(
                org.forgerock.json.JsonValue.object(
                        org.forgerock.json.JsonValue.field("spec", spec),
                        org.forgerock.json.JsonValue.field("failOnResponseViolation", true)));

        final Object created = new OpenApiValidationFilter.Heaplet()
                .create(Name.of("testFilter"), config, heap);

        assertThat(created).isInstanceOf(OpenApiValidationFilter.class);
    }

    private static Request buildGetRequest(final String uri) throws Exception {
        final Request r = new Request();
        r.setMethod("GET");
        r.setUri(uri);
        return r;
    }

    private static Request buildPostRequest(final String uri, final String body) throws Exception {
        final Request r = new Request();
        r.setMethod("POST");
        r.setUri(uri);
        r.getHeaders().put("Content-Type", "application/json");
        r.setEntity(body);
        return r;
    }

    private static ValidationReport singleErrorReport(final String message) {
        return ValidationReport.singleton(
                ValidationReport.Message.create("test.error.key", message).build());
    }
}
