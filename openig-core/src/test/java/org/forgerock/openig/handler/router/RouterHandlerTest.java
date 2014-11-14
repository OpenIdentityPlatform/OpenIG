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

package org.forgerock.openig.handler.router;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.handler.router.Files.*;
import static org.forgerock.openig.io.TemporaryStorage.*;
import static org.forgerock.openig.log.LogLevel.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.forgerock.util.Utils.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;

import org.forgerock.http.io.IO;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouterHandlerTest {

    @Mock
    private Heap heap;

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
        when(heap.get(TEMPORARY_STORAGE_HEAP_KEY, TemporaryStorage.class)).thenReturn(new TemporaryStorage());
        when(heap.get(LOGSINK_HEAP_KEY, LogSink.class)).thenReturn(new NullLogSink());
        routes = getTestResourceDirectory("routes");
        supply = getTestResourceDirectory("supply");
    }

    @Test
    public void testReactionsToDirectoryContentChanges() throws Exception {

        RouterHandler handler = new RouterHandler(new RouteBuilder(heap, Name.of("anonymous")),
                                                  new DirectoryMonitor(routes));

        // Initial scan
        handler.start();

        // Verify that the initial route is active
        assertStatusAfterHandle(handler, "OpenIG", 42);

        // Copy the 2nd route into the monitored directory
        File destination = copyFileFromSupplyToRoutes("addition.json");

        // Verify that both routes are active
        assertStatusAfterHandle(handler, "OpenIG", 42);
        assertStatusAfterHandle(handler, "OpenAM", 404);

        // Delete the additional file
        assertThat(destination.delete()).isTrue();

        // Verify that the first route is still active
        assertStatusAfterHandle(handler, "OpenIG", 42);

        // Verify that the second route is inactive
        Exchange fifth = new Exchange();
        fifth.put("name", "OpenAM");
        try {
            handler.handle(fifth);
            failBecauseExceptionWasNotThrown(HandlerException.class);
        } catch (HandlerException e) {
            assertThat(e).hasMessage("no handler to dispatch to");
        }

        handler.stop();
    }

    @Test
    public void testStoppingTheHandler() throws Exception {
        RouterHandler handler = new RouterHandler(new RouteBuilder(heap, Name.of("anonymous")),
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
                new RouterHandler(new RouteBuilder(heap, Name.of("anonymous")), new DirectoryMonitor(routes));

        // Initial scan
        handler.start();

        // Verify that the initial route is active
        assertStatusAfterHandle(handler, "OpenIG", 42);

        try {
            // Should throw since no routes match and there is no default handler.
            handle(handler, "OpenAM");
            failBecauseExceptionWasNotThrown(HandlerException.class);
        } catch (HandlerException e) {
            // Ok - the request could not be routed.
        }

        Handler defaultHandler = mock(Handler.class);
        handler.setDefaultHandler(defaultHandler);

        // Should route to default handler.
        Exchange exchange = handle(handler, "OpenAM");
        verify(defaultHandler).handle(exchange);
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
        RouterHandler router = new RouterHandler(new RouteBuilder(heap, Name.of("anonymous")), scanner);

        File before = Files.getRelativeFile(RouterHandlerTest.class, "clash/01-default.json");
        File after = Files.getRelativeFile(RouterHandlerTest.class, "clash/default.json");
        Exchange exchange = new Exchange();

        // Register the initial route
        router.onChanges(new FileChangeSet(null,
                                           Collections.singleton(before),
                                           Collections.<File>emptySet(),
                                           Collections.<File>emptySet()));

        router.handle(exchange);

        // Simulate file renaming
        router.onChanges(new FileChangeSet(null,
                                           Collections.singleton(after),
                                           Collections.<File>emptySet(),
                                           Collections.singleton(before)));

        router.handle(exchange);

    }

    @Test
    public void testDuplicatedRouteNamesAreGeneratingErrors() throws Exception {
        RouterHandler router = new RouterHandler(new RouteBuilder(heap, Name.of("anonymous")), scanner);
        router.logger = logger;

        File first = Files.getRelativeFile(RouterHandlerTest.class, "names/abcd-route.json");
        File second = Files.getRelativeFile(RouterHandlerTest.class, "names/another-abcd-route.json");

        // Register both routes
        router.onChanges(new FileChangeSet(null,
                                           new HashSet<File>(asList(first, second)),
                                           Collections.<File>emptySet(),
                                           Collections.<File>emptySet()));

        // Should have an error log statement
        verify(logger).logMessage(eq(ERROR), matches(".* A route named '.*' is already registered"));
    }

    private void assertStatusAfterHandle(final RouterHandler handler,
                                         final String value,
                                         final int expected) throws HandlerException, IOException {
        Exchange exchange = handle(handler, value);
        assertThat(exchange.response.getStatus()).isEqualTo(expected);
    }

    private Exchange handle(final RouterHandler handler, final String value)
            throws HandlerException, IOException {
        Exchange exchange = new Exchange();
        exchange.put("name", value);
        handler.handle(exchange);
        return exchange;
    }
}
