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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

import static java.lang.String.format;
import static org.forgerock.openig.config.Environment.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.HeapUtil.getObject;
import static org.forgerock.openig.heap.HeapUtil.getRequiredObject;
import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.LogSink;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * The main OpenIG HTTP Servlet which is responsible for bootstrapping the configuration and delegating all request
 * processing to the configured handler implementation (for example, a DispatchHandler).
 * <p>
 * <pre>
 *   {
 *      "heap": {
 *          ...
 *      },
 *      "handlerObject": "DispatchHandler",
 *      "baseURI": "http://localhost:8080",
 *      "logSink":  "myCustomLogSink",
 *      "temporaryStorage": "myCustomStorage"
 *   }
 * </pre>
 * {@literal handlerObject} is the only mandatory configuration attribute.
 */
public class GatewayServlet extends HttpServlet {

    /** Methods that should not include an entity body. */
    private static final CaseInsensitiveSet NON_ENTITY_METHODS = new CaseInsensitiveSet(Arrays.asList("GET", "HEAD",
            "TRACE", "DELETE"));

    private static final long serialVersionUID = 1L;

    private static JsonValue readJson(final URL resource) throws ServletException {
        InputStreamReader reader = null;
        try {
            final InputStream in = resource.openStream();
            final JSONParser parser = new JSONParser();
            reader = new InputStreamReader(in);
            return new JsonValue(parser.parse(reader));
        } catch (final ParseException e) {
            throw new ServletException(format("Cannot parse %s, probably because of some malformed Json", resource), e);
        } catch (final FileNotFoundException e) {
            throw new ServletException(format("File %s does not exists", resource), e);
        } catch (final IOException e) {
            throw new ServletException(format("Cannot read content of %s", resource), e);
        } finally {
            closeSilently(reader);
        }
    }

    /** Overrides request URLs constructed by container; making requests relative to a new base URI. */
    private URI baseURI;

    /**
     * Environment can be provided by the caller or, if null, it will be based on default policy.
     * <ol>
     * <li>{@literal openig-base} servlet init-param</li>
     * <li>{@literal OPENIG_BASE} environment variable</li>
     * <li>{@literal openig.base} system property</li>
     * <li>platform specific default directory ({@literal ~/.openig/} or {@literal $AppData$\openig\})</li>
     * </ol>
     */
    private Environment environment;

    /** The handler to dispatch exchanges to. */
    private Handler handler;

    /**
     * Heap of objects (represents the live configuration).
     */
    private HeapImpl heap;

    /** Provides methods for various logging activities. */
    private Logger logger;

    /** Allocates temporary buffers for caching streamed content during request processing. */
    private TemporaryStorage storage;

    /**
     * Default constructor invoked from web container. The servlet will be assumed to be running as a web application
     * and obtain its configuration from the default web {@linkplain Environment environment}.
     */
    public GatewayServlet() {
        this(null);
    }

    /**
     * Creates a new servlet using the provided environment. This constructor should be called when running the servlet
     * as part of a standalone application.
     *
     * @param environment
     *            The application environment.
     */
    public GatewayServlet(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void destroy() {
        heap.destroy();
        environment = null;
    }

    @Override
    public void init(final ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        if (environment == null) {
            environment = new WebEnvironment(servletConfig);
        }
        try {
            // Load the configuration
            final File configuration = new File(environment.getConfigDirectory(), "config.json");
            final URL configurationURL = configuration.canRead() ? configuration.toURI().toURL() : getClass()
                    .getResource("default-config.json");
            final JsonValue config = readJson(configurationURL);

            // Create and configure the heap
            heap = new HeapImpl();
            // "Live" objects
            heap.put("ServletContext", servletConfig.getServletContext());
            heap.put(ENVIRONMENT_HEAP_KEY, environment);

            // can be overridden in config
            final TemporaryStorage temporaryStorage = new TemporaryStorage();
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, temporaryStorage);
            heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
            heap.put(HttpClient.HTTP_CLIENT_HEAP_KEY, new HttpClient(temporaryStorage));
            heap.init(config.get("heap").required().expect(Map.class));

            handler = getRequiredObject(heap, config.get("handlerObject"), Handler.class);
            baseURI = config.get("baseURI").asURI();
            // As all heaplets can specify their own storage and logger,
            // those two lines provide custom logger or storage available.
            logger = new Logger(getObject(heap, config.get("logSink").defaultTo(LOGSINK_HEAP_KEY),
                    LogSink.class), "GatewayServlet");
            storage = getRequiredObject(heap, config.get("temporaryStorage").defaultTo(TEMPORARY_STORAGE_HEAP_KEY),
                    TemporaryStorage.class);
        } catch (final ServletException e) {
            throw e;
        } catch (final Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Handles a servlet request by dispatching it to a handler. It receives a servlet request, translates it into an
     * exchange object, dispatches the exchange to a handler, then translates the exchange response into the servlet
     * response.
     *
     * @param request
     *            the {@link HttpServletRequest} object that will be used to populate the initial OpenIG's
     *            {@link Request} encapsulated in the {@link Exchange}.
     * @param response
     *            the {@link HttpServletResponse} object that contains the response the servlet returns to the client.
     * @exception IOException
     *                if an input or output error occurs while the servlet is handling the HTTP request.
     * @exception ServletException
     *                if the HTTP request cannot be handled.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
            ServletException {
        final LogTimer timer = logger.getTimer().start();
        final Exchange exchange = new Exchange();
        // populate request
        exchange.request = new Request();
        exchange.request.setMethod(request.getMethod());
        try {
            exchange.request.setUri(new URI(request.getScheme(),
                                            null,
                                            request.getServerName(),
                                            request.getServerPort(),
                                            request.getRequestURI(),
                                            request.getQueryString(),
                                            null));
            if (baseURI != null) {
                exchange.request.getUri().rebase(baseURI);
            }
        } catch (final URISyntaxException use) {
            throw new ServletException(use);
        }
        // request headers
        for (final Enumeration<String> e = request.getHeaderNames(); e.hasMoreElements();) {
            final String name = e.nextElement();
            exchange.request.getHeaders().addAll(name, Collections.list(request.getHeaders(name)));
        }

        // include request entity if appears to be provided with request
        if ((request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null)
                && !NON_ENTITY_METHODS.contains(exchange.request.getMethod())) {
            exchange.request.setEntity(new BranchingStreamWrapper(request.getInputStream(), storage));
        }
        // remember request entity so that it (and its children) can be properly closed
        exchange.session = new ServletSession(request);
        exchange.principal = request.getUserPrincipal();
        // handy servlet-specific attributes, sure to be abused by downstream filters
        exchange.put(HttpServletRequest.class.getName(), request);
        exchange.put(HttpServletResponse.class.getName(), response);
        try {
            // handle request
            try {
                handler.handle(exchange);
            } catch (final HandlerException he) {
                throw new ServletException(he);
            }
            /*
             * Support for OPENIG-94/95 - The wrapped servlet may have already committed its response w/o creating a new
             * OpenIG Response instance in the exchange.
             */
            if (exchange.response != null) {
                // response status-code (reason-phrase deprecated in Servlet API)
                response.setStatus(exchange.response.getStatus());

                // response headers
                for (final String name : exchange.response.getHeaders().keySet()) {
                    for (final String value : exchange.response.getHeaders().get(name)) {
                        if (value != null && value.length() > 0) {
                            response.addHeader(name, value);
                        }
                    }
                }
                // response entity (if applicable)
                exchange.response.getEntity().copyRawContentTo(response.getOutputStream());
            }
        } finally {
            // final cleanup
            closeSilently(exchange.request, exchange.response);
        }
        timer.stop();
    }
}
