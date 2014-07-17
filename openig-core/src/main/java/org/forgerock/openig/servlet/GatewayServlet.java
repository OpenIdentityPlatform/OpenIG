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
import static org.forgerock.openig.io.TemporaryStorage.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.log.LogSink.LOGSINK_HEAP_KEY;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.WebEnvironment;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * The main OpenIG HTTP Servlet which is responsible for bootstrapping the
 * configuration and delegating all request processing to the configured HTTP
 * servlet implementation (e.g. HandlerServlet).
 */
public class GatewayServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * Environment can be provided by the caller or, if null, it will be based on default policy.
     * <ol>
     *     <li>{@literal openig-base} servlet init-param</li>
     *     <li>{@literal OPENIG_BASE} environment variable</li>
     *     <li>{@literal openig.base} system property</li>
     *     <li>platform specific default directory ({@literal ~/.openig/} or {@literal $AppData$\openig\})</li>
     * </ol>
     */
    private Environment environment;

    /**
     * Servlet to delegate requests to.
     */
    private HttpServlet servlet;

    /**
     * Heap of objects (represents the live configuration).
     */
    private HeapImpl heap;

    /**
     * Default constructor invoked from web container. The servlet will be assumed to be running as a web
     * application and obtain its configuration from the default web {@linkplain Environment environment}.
     */
    public GatewayServlet() {
        this(null);
    }

    /**
     * Creates a new servlet using the provided environment. This constructor
     * should be called when running the servlet as part of a standalone
     * application.
     *
     * @param environment The application environment.
     */
    public GatewayServlet(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void init(final ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        if (environment == null) {
            environment = new WebEnvironment(servletConfig);
        }
        try {
            // Load the configuration
            File configuration = new File(environment.getConfigDirectory(), "config.json");
            JsonValue config = readJson(configuration);

            // Create and configure the heap
            heap = new HeapImpl();
            // "Live" objects
            heap.put("ServletContext", servletConfig.getServletContext());
            heap.put(ENVIRONMENT_HEAP_KEY, environment);

            // can be overridden in config
            heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
            heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
            heap.init(config.get("heap").required().expect(Map.class));
            servlet = HeapUtil.getRequiredObject(heap, config.get("servletObject").required(), HttpServlet.class);
        } catch (HeapException he) {
            throw new ServletException(he);
        } catch (JsonValueException jve) {
            throw new ServletException(jve);
        }
    }

    private JsonValue readJson(final File resource) throws ServletException {
        InputStreamReader reader = null;
        try {
            InputStream in = new FileInputStream(resource);
            JSONParser parser = new JSONParser();
            reader = new InputStreamReader(in);
            return new JsonValue(parser.parse(reader));
        } catch (ParseException e) {
            throw new ServletException(format("Cannot parse %s, probably because of some malformed Json", resource), e);
        } catch (FileNotFoundException e) {
            throw new ServletException(format("File %s does not exists", resource), e);
        } catch (IOException e) {
            throw new ServletException(format("Cannot read content of %s", resource), e);
        } finally {
            closeSilently(reader);
        }
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        servlet.service(request, response);
    }

    @Override
    public void destroy() {
        heap.destroy();
        servlet = null;
        environment = null;
    }
}
