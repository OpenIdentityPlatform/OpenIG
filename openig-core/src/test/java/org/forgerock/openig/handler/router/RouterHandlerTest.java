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

package org.forgerock.openig.handler.router;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.handler.router.Files.getTestResourceDirectory;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.util.Utils.closeSilently;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;

import org.forgerock.http.Handler;
import org.forgerock.http.io.IO;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouterHandlerTest {

    private HeapImpl heap;

    @Mock
    private TimeService time;

    @Mock
    private DirectoryScanner scanner;

    @Spy
    private Logger logger = new Logger(new NullLogSink(), Name.of("source"));

    private File routes;
    private File supply;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        routes = getTestResourceDirectory("routes");
        supply = getTestResourceDirectory("supply");
        heap = buildDefaultHeap();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        DestroyDetectHandler.destroyed = false;
    }

    @Test
    public void testReactionsToDirectoryContentChanges() throws Exception {

        RouterHandler handler = new RouterHandler(newRouterBuilder(),
                                                  new DirectoryMonitor(routes));

        // Initial scan
        handler.start();

        // Verify that the initial route is active
        assertStatusAfterHandle(handler, "OpenIG", Status.TEAPOT);

        // Copy the 2nd route into the monitored directory
        File destination = copyFileFromSupplyToRoutes("addition.json");

        // Verify that both routes are active
        assertStatusAfterHandle(handler, "OpenIG", Status.TEAPOT);
        assertStatusAfterHandle(handler, "OpenAM", Status.NOT_FOUND);

        // Delete the additional file
        assertThat(destination.delete()).isTrue();

        // Verify that the first route is still active
        assertStatusAfterHandle(handler, "OpenIG", Status.TEAPOT);

        // Verify that the second route is inactive
        AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("name", "OpenAM");
        Response response = handler.handle(context, new Request()).get();
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        assertThat(response.getEntity().getString()).isEqualTo("no handler to dispatch to");

        handler.stop();
    }

    private RouteBuilder newRouterBuilder() {
        return new RouteBuilder(heap, Name.of("anonymous"), new EndpointRegistry(new Router()));
    }

    @Test
    public void testStoppingTheHandler() throws Exception {
        RouterHandler handler = new RouterHandler(newRouterBuilder(),
                                                  new DirectoryMonitor(routes));

        // Initial scan
        handler.start();
        assertThat(DestroyDetectHandler.destroyed).isFalse();

        handler.stop();
        assertThat(DestroyDetectHandler.destroyed).isTrue();
    }

    @Test
    public void testDefaultHandler() throws Exception {
        RouterHandler handler =
                new RouterHandler(newRouterBuilder(), new DirectoryMonitor(routes));

        // Initial scan
        handler.start();

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
        result.handleResult(new Response());
        // Mock the handler to return the promise
        Handler defaultHandler = mock(Handler.class);
        when(defaultHandler.handle(any(Context.class), any(Request.class))).thenReturn(result);
        return defaultHandler;
    }

    private File copyFileFromSupplyToRoutes(final String filename) throws IOException {
        File destination;
        Reader reader = null;
        Writer writer = null;
        try {
            reader = new FileReader(new File(supply, filename));
            destination = new File(routes, filename);
            destination.deleteOnExit();
            writer = new FileWriter(destination);
            IO.stream(reader, writer);
            writer.flush();
        } finally {
            closeSilently(reader, writer);
        }
        return destination;
    }

    @Test
    public void testRouteFileRenamingKeepingTheSameRouteName() throws Exception {
        RouterHandler router = new RouterHandler(newRouterBuilder(), scanner);

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

    @Test
    public void testDuplicatedRouteNamesAreGeneratingErrors() throws Exception {
        RouterHandler router = new RouterHandler(newRouterBuilder(), scanner);
        router.setLogger(logger);

        File first = Files.getRelativeFile(RouterHandlerTest.class, "names/abcd-route.json");
        File second = Files.getRelativeFile(RouterHandlerTest.class, "names/another-abcd-route.json");

        // Register both routes
        router.onChanges(new FileChangeSet(null,
                                           new HashSet<>(asList(first, second)),
                                           Collections.<File>emptySet(),
                                           Collections.<File>emptySet()));

        // Should have an error log statement
        verify(logger).error(anyString());
    }

    @Test
    public void testUncheckedExceptionSupportForAddedFiles() throws Exception {
        RouteBuilder builder = spy(newRouterBuilder());
        RouterHandler router = new RouterHandler(builder, scanner);
        router.setLogger(logger);

        doThrow(new NullPointerException()).when(builder).build(any(File.class));

        router.onChanges(new FileChangeSet(null,
                                           Collections.singleton(new File("/")),
                                           Collections.<File>emptySet(),
                                           Collections.<File>emptySet()));

        verify(logger).error(matches("The route defined in file '.*' cannot be added"));
    }

    @Test
    public void testRouterEndpointIsBeingRegistered() throws Exception {
        Router router = new Router();
        heap.put(Keys.ENDPOINT_REGISTRY_HEAP_KEY, new EndpointRegistry(router));
        heap.put(Keys.ENVIRONMENT_HEAP_KEY, new DefaultEnvironment(new File("dont-care")));
        heap.put(Keys.TIME_SERVICE_HEAP_KEY, TimeService.SYSTEM);

        RouterHandler handler = (RouterHandler) new RouterHandler.Heaplet()
                .create(Name.of("this-router"),
                        json(object(field("directory", getTestResourceDirectory("endpoints").getPath()))),
                        heap);
        handler.start();

        // Ping the 'routes' and intermediate endpoints
        Request ping = new Request();
        assertThat(router.handle(new RootContext(),
                                 ping.setUri("/this-router"))
                         .get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(router.handle(new RootContext(),
                                 ping.setUri("/this-router/routes/"))
                         .get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(router.handle(new RootContext(),
                                 ping.setUri("/this-router/routes/route-with-name"))
                         .get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(router.handle(new RootContext(),
                                 ping.setUri("/this-router/routes/route-with-name/objects"))
                         .get().getStatus())
                .isEqualTo(Status.NO_CONTENT);
        assertThat(router.handle(new RootContext(),
                                 ping.setUri("/this-router/routes/route-with-name/objects/register"))
                         .get().getStatus())
                .isEqualTo(Status.NO_CONTENT);

        // Ensure that this RouterHandler /routes endpoint is working
        Request request1 = new Request().setUri("/this-router/routes/route-with-name/objects/register/ping");
        Response response1 = router.handle(new RootContext(), request1).get();
        assertThat(response1.getEntity().getString()).isEqualTo("Pong");

        // Here's an URI when no heap object name is provided
        String uri = "/this-router/routes/without-name/objects"
                + "/orgforgerockopenighandlerrouterroutebuildertestregisterroutehandler-handler/ping";
        Request request2 = new Request().setUri(uri);
        Response response2 = router.handle(new RootContext(), request2).get();
        assertThat(response2.getEntity().getString()).isEqualTo("Pong");
    }

    private void assertStatusAfterHandle(final RouterHandler handler,
                                         final String value,
                                         final Status expected) throws Exception {
        Response response = handle(handler, value);
        assertThat(response.getStatus()).isEqualTo(expected);
    }

    private Response handle(final RouterHandler handler, final String value)
            throws Exception {
        AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("name", value);
        return handler.handle(context, new Request()).getOrThrow();
    }
}
