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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.config.Config;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.resource.ResourceException;

/**
 * The main OpenIG HTTP Servlet which is responsible for bootstrapping the
 * configuration and delegating all request processing to the configured HTTP
 * servlet implementation (e.g. HandlerServlet).
 */
public class GatewayServlet extends HttpServlet {
    private static final String PRODUCT_NAME = "OpenIG";

    private static final long serialVersionUID = 1L;

    private final Environment environment;
    private HttpServlet servlet;

    /**
     * Default constructor invoked from web container. The servlet will be
     * assumed to be running as a web application and obtain its configuration
     * and scripts from the ".openig" directory in the user's home directory.
     */
    public GatewayServlet() {
        this(Environment.forWebApp(PRODUCT_NAME));
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
    public void init() throws ServletException {
        try {
            ServletContext context = getServletConfig().getServletContext();
            JsonValue config = new Config(environment.getConfigResource(context)).read();
            HeapImpl heap = new HeapImpl();
            heap.put("ServletContext", context); // can be overridden in config
            heap.put("TemporaryStorage", new TemporaryStorage()); // can be overridden in config
            heap.put("LogSink", new ConsoleLogSink()); // can be overridden in config
            heap.put("Environment", environment);
            heap.init(config.get("heap").required().expect(Map.class));
            servlet = HeapUtil.getRequiredObject(heap, config.get("servletObject").required(), HttpServlet.class);
        } catch (HeapException he) {
            throw new ServletException(he);
        } catch (JsonValueException jve) {
            throw new ServletException(jve);
        } catch (ResourceException re) {
            throw new ServletException(re);
        }
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        servlet.service(request, response);
    }
}
