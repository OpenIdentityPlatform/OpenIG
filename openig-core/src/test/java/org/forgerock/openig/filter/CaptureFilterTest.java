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

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Iterator;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.NullOutputStream;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CaptureFilterTest {

    @Mock
    private Handler terminalHandler;

    @Mock
    private CaptureFilter.WriterProvider provider;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCaptureUnconditionallyOnBothRequestAndResponse() throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);

        StringWriter req = new StringWriter();
        PrintWriter requestWriter = new PrintWriter(req);
        StringWriter resp = new StringWriter();
        PrintWriter responseWriter = new PrintWriter(resp);
        when(provider.getWriter()).thenReturn(requestWriter, responseWriter);

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.response = new Response();

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);

        assertThat(req.toString()).contains("--- REQUEST 1 --->");
        assertThat(resp.toString()).contains("<--- RESPONSE 1 ---");
    }

    @Test
    public void testCaptureIsDisabledUnderCondition() throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);
        filter.setCondition(new Expression("${false}"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.response = new Response();

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);
        verifyZeroInteractions(provider);
    }

    @Test
    public void testMultiValuedHeadersAreCaptured() throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);

        // We only prepare the request writer
        StringWriter req = setupProviderForRequestWriter();

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.getHeaders().putSingle("Host", "openig.forgerock.org");
        exchange.request.getHeaders().add("Multi", "First");
        exchange.request.getHeaders().add("Multi", "Second");
        exchange.request.getHeaders().add("Multi", "Third");
        exchange.response = new Response();

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);

        assertThat(req.toString())
                .contains("Host: openig.forgerock.org")
                .contains("Multi: First")
                .contains("Multi: Second")
                .contains("Multi: Third");
    }

    @Test
    public void testIgnoreEntityCapture() throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);
        filter.setCaptureEntity(false);

        // We only prepare the request writer
        StringWriter req = setupProviderForRequestWriter();

        Exchange exchange = buildRequestOnlyExchange("this is an entity", "text/plain");

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);
        assertThat(req.toString())
                .contains("[entity]")
                .doesNotContain("this is an entity");
    }

    @Test(dataProvider = "blackListedMimeTypes")
    public void testBinaryEntityAreOnlyMarkedAndNotFullyCaptured(final String mimeType) throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);
        filter.setCaptureEntity(true);

        // We only prepare the request writer
        StringWriter req = setupProviderForRequestWriter();

        String entity = "this is a binary entity";
        Exchange exchange = buildRequestOnlyExchange(entity, mimeType);

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);
        assertThat(req.toString())
                .contains("[binary entity]")
                .doesNotContain(entity);
    }

    @Test(dataProvider = "acceptedMimeTypes")
    public void testAcceptedMimeTypesForEntitiesAreCaptured(final String mimeType) throws Exception {
        CaptureFilter filter = new CaptureFilter();
        filter.setWriterProvider(provider);
        filter.setCaptureEntity(true);

        // We only prepare the request writer
        StringWriter req = setupProviderForRequestWriter();

        String entity = "this is an entity";
        Exchange exchange = buildRequestOnlyExchange(entity, mimeType);

        filter.filter(exchange, terminalHandler);

        verify(terminalHandler).handle(exchange);
        assertThat(req.toString())
                .contains(entity);
    }

    /**
     * Configure the provider mock to return a {@link java.io.StringWriter} that will be
     * returned for later verification of content.
     */
    private StringWriter setupProviderForRequestWriter() throws IOException {

        // Create the PrintWriter for the request
        StringWriter writer = new StringWriter();
        PrintWriter requestWriter = new PrintWriter(writer);

        // 2nd time the getWriter() method is called, it will return a no-op writer
        when(provider.getWriter()).thenReturn(
                requestWriter,
                new PrintWriter(new NullOutputStream())
        );

        // returns a handle to the writer for content verification
        return writer;
    }

    /**
     * Build a simplistic Exchange with minimal request/response objects.
     */
    private Exchange buildRequestOnlyExchange(final String entity, final String mimeType) {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity(entity);
        exchange.request.getHeaders().putSingle("Content-type", mimeType);
        exchange.response = new Response();
        return exchange;
    }

    @DataProvider
    public Iterator<Object[]> acceptedMimeTypes() {
        return Arrays.asList(
                // white-listed mime types
                new Object[] {"application/atom+xml"},
                new Object[] {"application/javascript"},
                new Object[] {"application/json"},
                new Object[] {"application/rss+xml"},
                new Object[] {"application/xhtml+xml"},
                new Object[] {"application/xml"},
                new Object[] {"application/xml-dtd"},
                new Object[] {"application/x-www-form-urlencoded"},
                // normal textual mime-types
                new Object[] {"text/plain"},
                new Object[] {"text/css"},
                new Object[] {"text/csv"}
        ).iterator();
    }

    @DataProvider
    public Iterator<Object[]> blackListedMimeTypes() {
        return Arrays.asList(
                new Object[] {"application/octet-stream"},
                new Object[] {"application/pdf"},
                new Object[] {"multipart/mixed"},
                new Object[] {"video/mp4"},
                new Object[] {"application/vnd.ms-excel"}
        ).iterator();
    }

}
