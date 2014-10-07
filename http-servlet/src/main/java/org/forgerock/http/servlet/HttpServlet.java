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

package org.forgerock.http.servlet;

import static org.forgerock.util.Utils.closeSilently;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.http.Session;
import org.forgerock.http.URIUtil;
import org.forgerock.http.io.IO;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.SuccessHandler;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

public class HttpServlet extends javax.servlet.http.HttpServlet {

    /** Methods that should not include an entity body. */
    private static final CaseInsensitiveSet NON_ENTITY_METHODS = new CaseInsensitiveSet(Arrays.asList("GET", "HEAD",
            "TRACE", "DELETE"));

    private final TemporaryStorage storage = new TemporaryStorage();
    
    private ServletApiVersionAdapter syncFactory;
    private Handler handler;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        syncFactory = ServletApiVersionAdapter.getInstance(getServletContext());
    }

    @Override
    protected void service(HttpServletRequest req, final HttpServletResponse resp) throws ServletException,
            IOException {
//        LogTimer timer = logger.getTimer().start(); //TODO Move LogTimer into fr-util so can use here?

        // populate request
        final Request request = new Request();
        request.setMethod(req.getMethod());
        try {
            request.setUri(URIUtil.create(req.getScheme(), null, req.getServerName(), req.getServerPort(),
                    req.getRequestURI(), req.getQueryString(), null));
//            if (baseURI != null) { //TODO is this needed? if so how will it be configured?
//                request.getUri().rebase(baseURI);
//            }
        } catch (URISyntaxException use) {
            throw new ServletException(use);
        }
        // request headers
        for (Enumeration<String> e = req.getHeaderNames(); e.hasMoreElements();) {
            String name = e.nextElement();
            request.getHeaders().addAll(name, Collections.list(req.getHeaders(name)));
        }

        // include request entity if appears to be provided with request
        if ((req.getContentLength() > 0 || req.getHeader("Transfer-Encoding") != null)
                && !NON_ENTITY_METHODS.contains(request.getMethod())) {
            request.setEntity(IO.newBranchingInputStream(req.getInputStream(), storage));
        }
        // remember request entity so that it (and its children) can be properly closed
        // TODO consider moving this below (when the exchange will be fully configured)
//        Session session = newSession(request, exchange); //TODO how to create new session without Response?
        Session session = null;
        Principal principal = req.getUserPrincipal();
        final Context context = new Context(session, principal);
        // handy servlet-specific attributes, sure to be abused by downstream filters
//        exchange.put(HttpServletRequest.class.getName(), request); //TODO will not exposing this cause filters to fail?
//        exchange.put(HttpServletResponse.class.getName(), response); //TODO will not exposing this cause filters to fail?

        // handle request
        final ServletSynchronizer sync = syncFactory.createServletSynchronizer(req, resp);

        handler.handle(context, request)
                .onSuccess(new SuccessHandler<Response>() {
                    @Override
                    public void handleResult(Response response) {
                        try {
                            writeResponse(resp, response);
                            sync.signalAndComplete();
                        } catch (IOException e) {
                            //TODO debug
                            sync.signalAndComplete(e);
                        } finally {
                            closeSilently(request, context.getSession(), response);
                        }
                    }
                })
                .onFailure(new FailureHandler<ResponseException>() {
                    @Override
                    public void handleError(ResponseException error) {
                        try {
                            writeResponse(resp, error.getResponse());
                            sync.signalAndComplete();
                        } catch (IOException e) {
                            //TODO debug
                            sync.signalAndComplete(e);
                        } finally {
                            closeSilently(request, context.getSession(), error.getResponse());
                        }
                    }
                });

        try {
            sync.awaitIfNeeded();
        } catch (final Exception e) {
            //TODO debug
            //TODO should this instead return an appropriate response?...
            throw new ServletException(e);
        }

//        timer.stop(); //TODO
    }

    private void writeResponse(HttpServletResponse resp, Response response) throws IOException {
        /* TODO is this still valid?
         * Support for OPENIG-94/95 - The wrapped servlet may have already committed its response w/o creating a new
         * OpenIG Response instance in the exchange.
         */
        if (response != null) {
            // response status-code (reason-phrase deprecated in Servlet API)
            resp.setStatus(response.getStatus());

            // response headers
            for (String name : response.getHeaders().keySet()) {
                for (String value : response.getHeaders().get(name)) {
                    if (value != null && value.length() > 0) {
                        resp.addHeader(name, value);
                    }
                }
            }
            // response entity (if applicable)
            response.getEntity().copyRawContentTo(resp.getOutputStream());
        }
    }

//    private Session newSession(HttpServletRequest request, Exchange exchange) { //TODO how will the sessionFactory be configured?
//        if (sessionFactory != null) {
//            return sessionFactory.build(exchange);
//        }
//        return new ServletSession(request);
//    }
}
