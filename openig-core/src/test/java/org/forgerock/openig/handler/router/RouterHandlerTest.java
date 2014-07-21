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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.forgerock.openig.handler.router.Files.getTestResourceDirectory;
import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.util.Utils.closeSilently;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouterHandlerTest {

    @Mock
    private Heap heap;

    @Mock
    private TimeService time;

    private File routes;
    private File supply;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(heap.get(TEMPORARY_STORAGE_HEAP_KEY)).thenReturn(new TemporaryStorage());
        routes = getTestResourceDirectory("routes");
        supply = getTestResourceDirectory("supply");
    }

    @Test
    public void testReactionsToDirectoryContentChanges() throws Exception {

        RouterHandler handler = new RouterHandler(new RouteBuilder(heap), new DirectoryMonitor(routes));

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
        RouterHandler handler = new RouterHandler(new RouteBuilder(heap), new DirectoryMonitor(routes));

        // Initial scan
        handler.start();
        assertThat(DestroyDetectHandler.destroyed).isFalse();

        handler.stop();
        assertThat(DestroyDetectHandler.destroyed).isTrue();
    }

    @Test
    public void testDefaultHandler() throws Exception {
        RouterHandler handler =
                new RouterHandler(new RouteBuilder(heap), new DirectoryMonitor(routes));

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
            Streamer.stream(reader, writer);
            writer.flush();
        } finally {
            closeSilently(reader, writer);
        }
        return destination;
    }

    private void assertStatusAfterHandle(final RouterHandler handler,
                                         final String value,
                                         final int expected) throws HandlerException, IOException {
        Exchange exchange = handle(handler, value);
        assertThat(exchange.response.status).isEqualTo(expected);
    }

    private Exchange handle(final RouterHandler handler, final String value)
            throws HandlerException, IOException {
        Exchange exchange = new Exchange();
        exchange.put("name", value);
        handler.handle(exchange);
        return exchange;
    }
}
