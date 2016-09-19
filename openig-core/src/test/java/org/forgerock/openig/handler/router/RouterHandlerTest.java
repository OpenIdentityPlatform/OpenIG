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
 */

package org.forgerock.openig.handler.router;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.handler.router.Files.getTestResourceDirectory;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.assertj.core.api.iterable.Extractor;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
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

@SuppressWarnings("javadoc")
public class RouterHandlerTest {

    private HeapImpl heap;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private ScheduledFuture scheduledFuture;

    @Mock
    private Logger logger;

    private File routes;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        routes = getTestResourceDirectory("routes");
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

        File first = Files.getRelativeFile(RouterHandlerTest.class, "names/abcd-route.json");
        File second = Files.getRelativeFile(RouterHandlerTest.class, "names/another-abcd-route.json");

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
                .containsExactly("zzz", "aaa");
    }

    @Test
    public void testRouterEndpointIsBeingRegistered() throws Exception {
        Router router = new Router();
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router, ""));
        heap.put(Keys.ENVIRONMENT_HEAP_KEY, new DefaultEnvironment(new File("dont-care")));
        heap.put(Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, Executors.newScheduledThreadPool(1));

        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", getTestResourceDirectory("endpoints").getPath()),
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
    public void testSchedulePeriodicDirectoryMonitoring() throws Exception {
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class),
                                                          eq(3_000L),
                                                          eq(3_000L),
                                                          eq(MILLISECONDS)))
                .thenReturn(scheduledFuture);

        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", getTestResourceDirectory("endpoints").getPath()),
                                   field("scanInterval", "3 seconds"))),
                       heap);
        heaplet.destroy();

        verify(scheduledFuture).cancel(eq(true));
    }

    @Test
    public void testScheduleOnlyOnceDirectoryMonitoring() throws Exception {
        RouterHandler.Heaplet heaplet = new RouterHandler.Heaplet();
        heaplet.create(Name.of("this-router"),
                       json(object(field("directory", getTestResourceDirectory("endpoints").getPath()),
                                   field("scanInterval", "disabled"))),
                       heap);
        heaplet.destroy();

        verifyZeroInteractions(scheduledExecutorService);
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
}
