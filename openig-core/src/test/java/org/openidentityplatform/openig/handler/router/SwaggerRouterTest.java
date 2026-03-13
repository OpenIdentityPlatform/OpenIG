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

package org.openidentityplatform.openig.handler.router;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.handler.router.DestroyDetectHandler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.Files.getRelativeDirectory;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SwaggerRouterTest {

    private static final String VALID_PET = "{ \"id\": 10, \"name\": \"Buddy\" }";

    private static final String INVALID_PET_MISSING_NAME =
            "{\n" +
                    "  \"id\": 10,\n" +
                    "  \"photoUrls\": [\"https://example.com\"],\n" +
                    "  \"tags\": [{ \"id\": 1, \"name\": \"friendly\" }],\n" +
                    "  \"status\": \"available\"\n" +
                    "}";


    private static final String INVALID_PETS_RESPONSE =
            "[\n" +
                    "  { \"id\": 10, \"status\": \"available\" },\n" +
                    "  { \"id\": 11, \"status\": \"pending\" }\n" +
                    "]";

    private static final String VALID_PETS_RESPONSE = "[]";

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private Handler testHandler;

    private SwaggerDirectoryMonitor directoryMonitor;

    private Context ctx;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        File swagger = getRelativeDirectory(SwaggerRouterTest.class, "swagger");
        directoryMonitor =  new SwaggerDirectoryMonitor(swagger);

        HeapImpl heap = buildDefaultHeap();
        heap.put(Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, scheduledExecutorService);

        ctx = new RootContext();
    }

    @AfterMethod
    public void tearDown() {
        DestroyDetectHandler.destroyed = false;
    }

    @Test
    public void getRequest_validRequest_returns200()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();
        stubUpstream(Status.OK, "application/json", VALID_PETS_RESPONSE);

        Request request = get("/pets");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }
    @Test
    public void postRequest_validPetBody_returns201()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();
        stubUpstream(Status.CREATED, null, null);

        Request request = post("/pets", "application/json", VALID_PET);

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.CREATED);
        verify(testHandler).handle(ctx, request);
    }

    @Test
    public void postRequest_missingRequiredField_returns400()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();

        Request request = post("/pets", "application/json", INVALID_PET_MISSING_NAME);

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        // The downstream handler must never be called when the request is invalid.
        verify(testHandler, never()).handle(any(), any());
    }


    @Test
    public void postRequest_malformedJson_returns400()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();

        Request request = post("/pets", "application/json", "not-json-at-all");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        verify(testHandler, never()).handle(any(), any());
    }

    @Test
    public void postRequest_wrongContentType_returns400()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();

        Request request = post("/pets", "text/plain", VALID_PET);

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        verify(testHandler, never()).handle(any(), any());
    }


    @Test
    public void getRequest_upstreamReturnsInvalidBody_returns502()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();
        stubUpstream(Status.OK, "application/json", INVALID_PETS_RESPONSE);

        Request request = get("/pets");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
    }

    @Test
    public void getRequest_upstreamReturnsUndefinedStatusCode_returns502()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();
        stubUpstream(Status.INTERNAL_SERVER_ERROR, "application/json", "{ \"error\": \"boom\" }");

        Request request = get("/pets");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
    }

    @Test
    public void getRequest_unknownPath_returns404()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();

        Request request = get("/unknown-path");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        verify(testHandler, never()).handle(any(), any());
    }

    @Test
    public void optionsRequest_methodNotInSpec_returns404()
            throws URISyntaxException, ExecutionException, InterruptedException {

        SwaggerRouter router = buildRouter();

        Request request = new Request();
        request.setMethod("OPTIONS");
        request.setUri("http://localhost:8080/pets");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        verify(testHandler, never()).handle(any(), any());
    }

    @Test
    public void handle_noSpecsLoaded_returns404()
            throws URISyntaxException, ExecutionException, InterruptedException {

        // Router is created but monitor.monitor() is NOT called, so no specs are loaded.
        SwaggerRouter router = new SwaggerRouter(testHandler);

        Request request = get("/pets");

        Response response = router.handle(ctx, request).get();

        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        verify(testHandler, never()).handle(any(), any());
    }


    @Test
    public void onChanges_addedFile_validatorIsRegistered() {

        // Start with a router that has no specs loaded.
        SwaggerRouter router = new SwaggerRouter(testHandler);
        assertThat(router.validators).isEmpty();

        directoryMonitor.monitor(router);

        assertThat(router.validators).isNotEmpty();
    }


    private SwaggerRouter buildRouter() {
        SwaggerRouter router = new SwaggerRouter(testHandler);
        directoryMonitor.monitor(router);
        return router;
    }

    private void stubUpstream(Status status, String contentType, String body) {
        when(testHandler.handle(any(Context.class), any(Request.class)))
                .thenAnswer(invocation -> {
                    Response resp = new Response(status);
                    if (contentType != null) {
                        resp.getHeaders().add("Content-Type", contentType);
                    }
                    if (body != null) {
                        resp.setEntity(body);
                    }
                    return Promises.newResultPromise(resp);
                });
    }

    private static Request get(String path) throws URISyntaxException {
        Request r = new Request();
        r.setMethod("GET");
        r.setUri("http://localhost:8080" + path);
        return r;
    }

     private static Request post(String path, String contentType, String body)
            throws URISyntaxException {
        Request r = new Request();
        r.setMethod("POST");
        r.setUri("http://localhost:8080" + path);
        r.getHeaders().add("Content-Type", contentType);
        r.setEntity(body);
        return r;
    }
}