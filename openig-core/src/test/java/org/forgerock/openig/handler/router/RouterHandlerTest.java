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
 * Portions copyright 2026 3A Systems LLC
 */

package org.forgerock.openig.handler.router;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.Files.getRelativeDirectory;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.openig.http.RunMode.EVALUATION;
import static org.forgerock.openig.http.RunMode.PRODUCTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import io.swagger.v3.oas.models.OpenAPI;
import org.assertj.core.api.iterable.Extractor;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.Files;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RouterHandlerTest {

    private HeapImpl heap;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private ScheduledFuture scheduledFuture;

    @Mock
    private Logger logger;

    @Mock
    private OpenApiSpecLoader mockSpecLoader;

    @Mock
    private OpenApiRouteBuilder mockOpenApiRouteBuilder;

    @Mock
    private RouteBuilder mockRouteBuilder;

    private File routes;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        routes = getRelativeDirectory(RouterHandlerTest.class, "routes");
        heap = buildDefaultHeap();
        heap.put(Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, scheduledExecutorService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        DestroyDetectHandler.destroyed = false;
    }

    private RouteBuilder newRouteBuilder() {
        return new RouteBuilder(heap, Name.of("anonymous"), new EndpointRegistry(new Router(), "/"));
    }

    @Test
    public void testStoppingTheHandler() throws Exception {
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(routes);
        RouterHandler handler = new RouterHandler(newRouteBuilder(), directoryMonitor);
        directoryMonitor.monitor(handler);

        assertThat(DestroyDetectHandler.destroyed).isFalse();
        handler.stop();
        assertThat(DestroyDetectHandler.destroyed).isTrue();
    }

    @Test
    public void testDefaultHandler() throws Exception {
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(routes);
        RouterHandler handler = new RouterHandler(newRouteBuilder(), directoryMonitor);
        directoryMonitor.monitor(handler);

        // Verify that the initial route is active
        assertStatusAfterHandle(handler, "OpenIG", Status.TEAPOT);

        // Should returns a 404 since no routes match and there is no default handler.
        Response response = handle(handler, "OpenAM");
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);

        Handler defaultHandler = mockDefaultHandler();
        handler.setDefaultHandler(defaultHandler);

        // Should route to default handler.
        handle(handler, "OpenAM");
        verify(defaultHandler).handle(any(Context.class), any(Request.class));
    }

    private Handler mockDefaultHandler() {
        // Create a successful promise
        PromiseImpl<Response, NeverThrowsException> result = PromiseImpl.create();
        result.handleResult(new Response(Status.OK));
        // Mock the handler to return the promise
        Handler defaultHandler = mock(Handler.class);
        when(defaultHandler.handle(any(Context.class), any(Request.class))).thenReturn(result);
        return defaultHandler;
    }

    @Test
    public void testRouteFileRenamingKeepingTheSameRouteName() throws Exception {
        RouterHandler router = new RouterHandler(newRouteBuilder(), new DirectoryMonitor(null));

        File before = Files.getRelativeFile(RouterHandlerTest.class, "clash/01-default.json");
        File after = Files.getRelativeFile(RouterHandlerTest.class, "clash/default.json");
        Context context = new RootContext();

        // Register the initial route
        router.onChanges(new FileChangeSet(null,
                                           Collections.singleton(before),
                                           Collections.<File>emptySet(),
                                           Collections.<File>emptySet()));

        router.handle(context, new Request()).getOrThrow();

        // Simulate file renaming
        router.onChanges(new FileChangeSet(null,
                                           Collections.singleton(after),
                                           Collections.<File>emptySet(),
                                           Collections.singleton(before)));

        router.handle(context, new Request()).getOrThrow();
    }

    @Test(expectedExceptions = RouterHandlerException.class)
    public void testLoadingOfRouteWithSameNameFailsSecondDeploymentAndOnlyActivateFirstRoute() throws Exception {
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(routes);
        RouterHandler handler = new RouterHandler(newRouteBuilder(), directoryMonitor);
        directoryMonitor.monitor(handler);

        JsonValue routeConfig = json(object(field("handler",
                                           object(field("type",
                                                        "org.forgerock.openig.handler.router.StatusHandler"),
                                                  field("config", object(field("status", 200)))))));

        // Load both routes
        handler.load("id1", "route-name", routeConfig.copy());
        handler.load("id2", "route-name", routeConfig.copy());
    }

    @Test
    public void testListOfRoutesIsProperlyOrdered() throws Exception {
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(null);
        RouterHandler handler = new RouterHandler(newRouteBuilder(), directoryMonitor);

        JsonValue routeConfig = json(object(field("handler",
                                                  object(field("type",
                                                               "org.forgerock.openig.handler.router.StatusHandler"),
                                                         field("config", object(field("status", 200)))))));

        // Load both routes
        handler.load("aaa", "01", routeConfig.copy());
        handler.load("zzz", "00", routeConfig.copy());

        assertThat(handler.getRoutes())
                .extracting(new Extractor<Route, String>() {
                    @Override
                    public String extract(Route input) {
                        return input.getId();
                    }
                })
                .containsExactly("aaa","zzz");
    }

    @Test
    public void testRouterEndpointIsBeingRegistered() throws Exception {
        Router router = new Router();
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router, ""));
        heap.put(Keys.ENVIRONMENT_HEAP_KEY, new DefaultEnvironment(new File("dont-care")));
        heap.put(Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, Executors.newScheduledThreadPool(1));
        heap.put(Keys.RUNMODE_HEAP_KEY, EVALUATION);

        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", endpointsDirectory().getPath()),
                                   field("scanInterval", "disabled"))),
                       heap);
        heaplet.start();

        // Ping the 'routes' and intermediate endpoints
        assertStatusOnUri(router, "/this-router", Status.NO_CONTENT);
        assertStatusOnUri(router, "/this-router/routes?_queryFilter=true", Status.OK);
        assertStatusOnUri(router, "/this-router/routes/with-name", Status.OK);
        assertStatusOnUri(router, "/this-router/routes/with-name/objects", Status.NO_CONTENT);
        assertStatusOnUri(router, "/this-router/routes/with-name/objects/register", Status.NO_CONTENT);

        // Ensure that this RouterHandler /routes endpoint is working
        Request request1 = new Request().setUri("/this-router/routes/with-name/objects/register/ping");
        Response response1 = router.handle(context(), request1).get();
        assertThat(response1.getEntity().getString()).isEqualTo("Pong");

        // Here's an URI when no heap object name is provided
        String uri = "/this-router/routes/without-name/objects"
                + "/orgforgerockopenighandlerrouterroutebuildertestregisterroutehandler-handler/ping";
        Request request2 = new Request().setUri(uri);
        Response response2 = router.handle(context(), request2).get();
        assertThat(response2.getEntity().getString()).isEqualTo("Pong");
    }

    @Test
    public void testRouterRoutesEndpointIsNotRegisteredInProductionMode() throws Exception {
        Router router = new Router();
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router, ""));
        heap.put(Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, Executors.newScheduledThreadPool(1));
        heap.put(Keys.RUNMODE_HEAP_KEY, PRODUCTION);

        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", endpointsDirectory().getPath()),
                                   field("scanInterval", "disabled"))),
                       heap);
        heaplet.start();

        // Ping the 'routes' and intermediate endpoints
        assertStatusOnUri(router, "/this-router", Status.NO_CONTENT);
        assertStatusOnUri(router, "/this-router/routes/with-name/objects", Status.NO_CONTENT);
        assertStatusOnUri(router, "/this-router/routes/with-name/objects/register", Status.NO_CONTENT);

        // Only /routes and /routes/routeId should not be exposed
        assertStatusOnUri(router, "/this-router/routes?_queryFilter=true", Status.NOT_FOUND);
        assertStatusOnUri(router, "/this-router/routes/with-name", Status.NOT_FOUND);
    }

    @Test
    public void testSchedulePeriodicDirectoryMonitoring() throws Exception {
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class),
                                                          eq(3_000L),
                                                          eq(3_000L),
                                                          eq(MILLISECONDS)))
                .thenReturn(scheduledFuture);

        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", endpointsDirectory().getPath()),
                                   field("scanInterval", "3 seconds"))),
                       heap);
        heaplet.destroy();

        verify(scheduledFuture).cancel(eq(true));
    }

    @Test
    public void testScheduleOnlyOnceDirectoryMonitoring() throws Exception {
        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", endpointsDirectory().getPath()),
                                   field("scanInterval", "disabled"))),
                       heap);
        heaplet.destroy();

        verifyNoMoreInteractions(scheduledExecutorService);
    }

    // OpenAPI tests

    @Test
    public void onChanges_deploysRoute_whenOpenApiSpecFileIsAdded() throws Exception {
        final File specFile = mock(File.class);
        final OpenAPI fakeSpec = new OpenAPI();
        final JsonValue routeJson = buildFakeRouteJson("petstore");
        final Route mockRoute     = mockRoute("petstore");

        when(mockSpecLoader.isOpenApiFile(specFile)).thenReturn(true);
        when(mockSpecLoader.tryLoad(specFile)).thenReturn(Optional.of(fakeSpec));
        when(mockOpenApiRouteBuilder.buildRouteJson(eq(fakeSpec), eq(specFile), anyBoolean())).thenReturn(routeJson);
        when(mockRouteBuilder.build(any(), any(), any())).thenReturn(mockRoute);

        RouterHandler handler = newHandler();
        handler.onChanges(addedChangeSet(specFile));

        verify(mockSpecLoader).tryLoad(specFile);
        verify(mockOpenApiRouteBuilder).buildRouteJson(fakeSpec, specFile, false);
        verify(mockRouteBuilder).build(any(), any(), any());
    }

    @Test
    public void onChanges_doesNotDeployRoute_whenSpecFileFails() throws Exception {

        final File brokenSpecFile = mock(File.class);

        when(mockSpecLoader.isOpenApiFile(brokenSpecFile)).thenReturn(true);
        when(mockSpecLoader.tryLoad(brokenSpecFile)).thenReturn(Optional.empty());

        RouterHandler handler = newHandler();
        handler.onChanges(addedChangeSet(brokenSpecFile));

        verify(mockRouteBuilder, never()).build(any(), any(), any());
    }

    @Test
    public void stop_destroysAllRoutes() throws Exception {
        final File specFile = mock(File.class);
        final OpenAPI fakeSpec  = new OpenAPI();
        final JsonValue routeJson = buildFakeRouteJson("petstore");
        final Route mockRoute = mockRoute("petstore");

        when(mockSpecLoader.isOpenApiFile(specFile)).thenReturn(true);
        when(mockSpecLoader.tryLoad(specFile)).thenReturn(Optional.of(fakeSpec));
        when(mockOpenApiRouteBuilder.buildRouteJson(eq(fakeSpec), eq(specFile), anyBoolean())).thenReturn(routeJson);
        when(mockRouteBuilder.build(any(), any(), any())).thenReturn(mockRoute);

        RouterHandler handler = newHandler();

        handler.onChanges(addedChangeSet(specFile));
        handler.stop();

        verify(mockRoute).destroy();
    }

    @Test
    public void onChanges_ignoresOpenApiSpecFile_whenEnabledIsFalse() throws Exception {

        RouterHandler handler = handlerWith(new RouterHandler.OpenApiValidationSettings(false, false));

        final File specFile = mock(File.class);

        // Even if the loader would recognise the file, the handler must skip it
        when(mockSpecLoader.isOpenApiFile(specFile)).thenReturn(true);

        handler.onChanges(addedChangeSet(specFile));

        // Neither the loader nor the route builder should have been consulted
        verify(mockSpecLoader, never()).tryLoad(any());
        verify(mockOpenApiRouteBuilder, never()).buildRouteJson(any(), any(), any(Boolean.class));
        verify(mockRouteBuilder, never()).build(any(), any(), any());
    }

    @Test
    public void buildRouteJson_isCalledWithFalse_whenFailOnResponseViolationIsFalse()
            throws Exception {
        final RouterHandler strictHandler = handlerWith(
                new RouterHandler.OpenApiValidationSettings(true, false));
        final File specFile       = mock(File.class);
        final OpenAPI fakeSpec    = new OpenAPI();
        final JsonValue routeJson = buildFakeRouteJson("api");
        final Route mockRoute     = mockRoute("api");

        when(mockSpecLoader.isOpenApiFile(specFile)).thenReturn(true);
        when(mockSpecLoader.tryLoad(specFile)).thenReturn(Optional.of(fakeSpec));
        when(mockOpenApiRouteBuilder.buildRouteJson(fakeSpec, specFile, false))
                .thenReturn(routeJson);
        when(mockRouteBuilder.build(any(), any(), any())).thenReturn(mockRoute);


        strictHandler.onChanges(addedChangeSet(specFile));

        // Must be called with failOnResponseViolation=false
        verify(mockOpenApiRouteBuilder).buildRouteJson(fakeSpec, specFile, false);
    }

    @Test
    public void buildRouteJson_isCalledWithTrue_whenFailOnResponseViolationIsTrue()
            throws Exception {
        final RouterHandler strictHandler = handlerWith(
                new RouterHandler.OpenApiValidationSettings(true, true));
        final File specFile       = mock(File.class);
        final OpenAPI fakeSpec    = new OpenAPI();
        final JsonValue routeJson = buildFakeRouteJson("api");
        final Route mockRoute     = mockRoute("api");

        when(mockSpecLoader.isOpenApiFile(specFile)).thenReturn(true);
        when(mockSpecLoader.tryLoad(specFile)).thenReturn(Optional.of(fakeSpec));
        when(mockOpenApiRouteBuilder.buildRouteJson(fakeSpec, specFile, true))
                .thenReturn(routeJson);
        when(mockRouteBuilder.build(any(), any(), any())).thenReturn(mockRoute);

        strictHandler.onChanges(addedChangeSet(specFile));

        // Must be called with failOnResponseViolation=true
        verify(mockOpenApiRouteBuilder).buildRouteJson(fakeSpec, specFile, true);
    }

    @Test
    public void openApiValidationSettings_failOnResponseViolation_defaultsToFalse() {
        final RouterHandler.OpenApiValidationSettings settings =
                new RouterHandler.OpenApiValidationSettings();
        assertThat(settings.failOnResponseViolation).isFalse();
    }

    @Test
    public void generatedRouteJson_containsFalse_whenFailOnResponseViolationIsFalse()
            throws Exception {
        // End-to-end: use the real OpenApiRouteBuilder to check the JSON it produces
        final OpenApiRouteBuilder realBuilder = new OpenApiRouteBuilder();
        final File specFile = mock(File.class);
        // Minimal parsed spec with one path
        final io.swagger.v3.oas.models.OpenAPI spec = new io.swagger.v3.oas.models.OpenAPI();
        spec.setInfo(new io.swagger.v3.oas.models.info.Info().title("Test").version("1"));
        spec.setPaths(new io.swagger.v3.oas.models.Paths());

        final JsonValue routeJson = realBuilder.buildRouteJson(spec, specFile, false);

        final java.util.List<Object> heap = routeJson.get("heap").asList();
        final java.util.Map<?, ?> validatorEntry = heap.stream()
                .filter(o -> o instanceof java.util.Map)
                .map(o -> (java.util.Map<?, ?>) o)
                .filter(m -> "OpenApiValidationFilter".equals(m.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No OpenApiValidationFilter in heap"));

        final java.util.Map<?, ?> cfg = (java.util.Map<?, ?>) validatorEntry.get("config");
        assertThat(cfg.get("failOnResponseViolation")).isEqualTo(false);
    }

    @Test
    public void generatedRouteJson_containsTrue_whenFailOnResponseViolationIsTrue()
            throws Exception {
        final OpenApiRouteBuilder realBuilder = new OpenApiRouteBuilder();
        final File specFile = mock(File.class);
        final io.swagger.v3.oas.models.OpenAPI spec = new io.swagger.v3.oas.models.OpenAPI();
        spec.setInfo(new io.swagger.v3.oas.models.info.Info().title("Test").version("1"));
        spec.setPaths(new io.swagger.v3.oas.models.Paths());

        final JsonValue routeJson = realBuilder.buildRouteJson(spec, specFile, true);

        final java.util.Map<?, ?> validatorEntry = routeJson.get("heap").asList().stream()
                .filter(o -> o instanceof java.util.Map)
                .map(o -> (java.util.Map<?, ?>) o)
                .filter(m -> "OpenApiValidationFilter".equals(m.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No OpenApiValidationFilter in heap"));

        final java.util.Map<?, ?> cfg = (java.util.Map<?, ?>) validatorEntry.get("config");
        assertThat(cfg.get("failOnResponseViolation")).isEqualTo(true);
    }

    private void assertStatusOnUri(Handler router, String uri, Status expected)
            throws URISyntaxException, ExecutionException, InterruptedException {
        Request ping = new Request().setMethod("GET");
        assertThat(router.handle(context(), ping.setUri(uri)).get().getStatus())
                .isEqualTo(expected);
    }

    private void assertStatusAfterHandle(final RouterHandler handler,
                                         final String value,
                                         final Status expected) throws Exception {
        Response response = handle(handler, value);
        assertThat(response.getStatus()).isEqualTo(expected);
    }

    private Response handle(final RouterHandler handler, final String value)
            throws Exception {
        AttributesContext context = context();
        context.getAttributes().put("name", value);
        return handler.handle(context, new Request()).getOrThrow();
    }

    private static AttributesContext context() {
        return new AttributesContext(new RootContext());
    }

    private static File endpointsDirectory() throws IOException {
        return getRelativeDirectory(RouteBuilderTest.class, "endpoints");
    }

    private static JsonValue buildFakeRouteJson(final String name) {
        return JsonValue.json(JsonValue.object(
                JsonValue.field("name", name),
                JsonValue.field("handler", JsonValue.object(
                        JsonValue.field("type", "Chain"),
                        JsonValue.field("config", JsonValue.object(
                                JsonValue.field("filters", List.of()),
                                JsonValue.field("handler", "ClientHandler")))))));
    }

    private static Route mockRoute(final String id) {
        final Route r = mock(Route.class);
        when(r.getId()).thenReturn(id);
        when(r.accept(any(), any())).thenReturn(false);
        return r;
    }

    private RouterHandler handlerWith(RouterHandler.OpenApiValidationSettings openApiValidationSettings) {
        return new RouterHandler(
                mockRouteBuilder,
                new DirectoryMonitor(routes),
                mockSpecLoader,
                mockOpenApiRouteBuilder, openApiValidationSettings);
    }
    private RouterHandler newHandler() {
        return handlerWith(new RouterHandler.OpenApiValidationSettings());
    }

    private FileChangeSet addedChangeSet(File route) {
        return new FileChangeSet(mock(File.class),  Set.of(route), Set.of(), Set.of());
    }

}
