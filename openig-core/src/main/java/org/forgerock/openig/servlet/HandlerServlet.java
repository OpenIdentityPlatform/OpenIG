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
 * Copyright © 2009 Sun Microsystems Inc. All rights reserved.
 * Portions Copyrighted 2010–2011 ApexIdentity Inc.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.io.BranchingInputStream;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.URIUtil;

/**
 * Translates between the Servlet API and the exchange object model.
 */
public class HandlerServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Overrides request URLs constructed by container; making requests relative to a new base URI. */
    protected URI baseURI;

    /** The handler to dispatch exchanges to. */
    protected Handler handler;

    /** Allocates temporary buffers for caching streamed content during request processing. */
    protected TemporaryStorage storage;

    /** Provides methods for various logging activities. */
    protected Logger logger;

    /** Methods that should not include an entity body. */
    private static final CaseInsensitiveSet NON_ENTITY_METHODS =
            new CaseInsensitiveSet(Arrays.asList("GET", "HEAD", "TRACE", "DELETE"));

    /**
     * Handles a servlet request by dispatching it to a handler. It receives a servlet request, translates it into an
     * exchange object, dispatches the exchange to a handler, then translates the exchange response into an servlet
     * response.
     *
     * @param request
     *            the {@link HttpServletRequest} object that contains the request the client made of the servlet
     * @param response
     *            the {@link HttpServletResponse} object that contains the response the servlet returns to the client
     * @exception IOException
     *                if an input or output error occurs while the servlet is handling the HTTP request
     * @exception ServletException
     *                if the HTTP request cannot be handled
     */
    @Override
    @SuppressWarnings("unchecked")
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        LogTimer timer = logger.getTimer().start();
        Exchange exchange = new Exchange();
        // populate request
        exchange.request = new Request();
        exchange.request.method = request.getMethod();
        try {
            exchange.request.uri = URIUtil.create(request.getScheme(), null, request.getServerName(),
                    request.getServerPort(), request.getRequestURI(), request.getQueryString(), null);
            if (baseURI != null) {
                exchange.request.uri = URIUtil.rebase(exchange.request.uri, baseURI);
            }
        } catch (URISyntaxException use) {
            throw new ServletException(use);
        }
        // request headers
        for (Enumeration<String> e = request.getHeaderNames(); e.hasMoreElements();) {
            String name = e.nextElement();
            exchange.request.headers.addAll(name, Collections.list(request.getHeaders(name)));
        }
        
        // Force parameters parsing otherwise request.getInputStream interferes with.
        request.getParameterNames();
        
        // include request entity if appears to be provided with request
        if ((request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null)
                && !NON_ENTITY_METHODS.contains(exchange.request.method)) {
            exchange.request.entity = new BranchingStreamWrapper(request.getInputStream(), storage);
        }
        // remember request entity so that it (and its children) can be properly closed
        BranchingInputStream requestEntityTrunk = exchange.request.entity;
        exchange.session = new ServletSession(request);
        exchange.principal = request.getUserPrincipal();
        // handy servlet-specific attributes, sure to be abused by downstream filters
        exchange.put(HttpServletRequest.class.getName(), request);
        exchange.put(HttpServletResponse.class.getName(), response);
        try {
            // handle request
            try {
                handler.handle(exchange);
            } catch (HandlerException he) {
                throw new ServletException(he);
            }
            /*
             * Support for OPENIG-94/95 - The wrapped servlet may have already committed its response w/o creating a new
             * OpenIG Response instance in the exchange.
             */
            if (exchange.response != null) {
                // response status-code (reason-phrase deprecated in Servlet API)
                response.setStatus(exchange.response.status);

                // response headers
                for (String name : exchange.response.headers.keySet()) {
                    for (String value : exchange.response.headers.get(name)) {
                        if (value != null && value.length() > 0) {
                            response.addHeader(name, value);
                        }
                    }
                }
                // response entity (if applicable)
                if (exchange.response.entity != null) {
                    OutputStream out = response.getOutputStream();
                    Streamer.stream(exchange.response.entity, out);
                    out.flush();
                }
            }
        } finally {
            // final cleanup
            if (requestEntityTrunk != null) {
                try {
                    requestEntityTrunk.close();
                } catch (IOException ioe) {
                    // ignore exception closing a stream
                }
            }
            if (exchange.response != null && exchange.response.entity != null) {
                try {
                    // important!
                    exchange.response.entity.close();
                } catch (IOException ioe) {
                    // ignore exception closing a stream
                }
            }
        }
        timer.stop();
    }

    /** Creates and initializes a handler servlet in a heap environment. */
    public static class Heaplet extends GenericServletHeaplet {
        @Override
        public HttpServlet createServlet() throws HeapException {
            HandlerServlet servlet = new HandlerServlet();
            servlet.handler = HeapUtil.getRequiredObject(heap, config.get("handler").required(), Handler.class);
            servlet.baseURI = config.get("baseURI").asURI();
            servlet.storage = this.storage;
            servlet.logger = this.logger;
            return servlet;
        }
    }
}
