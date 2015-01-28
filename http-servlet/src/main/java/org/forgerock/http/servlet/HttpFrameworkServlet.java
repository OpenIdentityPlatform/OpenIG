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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.http.servlet;

import static org.forgerock.http.io.IO.newBranchingInputStream;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.util.Utils.closeSilently;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.forgerock.http.ClientInfoContext;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.HttpContext;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.http.Session;
import org.forgerock.http.URIUtil;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.resource.core.Context;
import org.forgerock.resource.core.ResourceName;
import org.forgerock.resource.core.RootContext;
import org.forgerock.resource.core.routing.RouterContext;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;

/**
 * <p>
 * An HTTP servlet implementation which provides integration between the Servlet
 * API and the common HTTP Framework.
 * </p>
 * <p>
 * A {@link HttpApplication} implementation must be registered in the
 * {@link ServiceLoader} framework
 * </p>
 *
 * @see HttpApplication
 * @since 1.0.0
 */
public final class HttpFrameworkServlet extends HttpServlet {
    private static final long serialVersionUID = 3524182656424860912L;

    /**
     * Standard specified request attribute name for retrieving X509 Certificates.
     */
    private static final String SERVLET_REQUEST_X509_ATTRIBUTE = "javax.servlet.request.X509Certificate";

    /** Methods that should not include an entity body. */
    private static final CaseInsensitiveSet NON_ENTITY_METHODS = new CaseInsensitiveSet(
            Arrays.asList("GET", "HEAD", "TRACE", "DELETE"));

    /**
     * Servlet 3.x defines ServletContext.TEMPDIR constant, but this does not
     * exist in Servlet 2.5, hence the constant redefined here.
     */
    private static final String SERVLET_TEMP_DIR = "javax.servlet.context.tempdir";

    private ServletVersionAdapter adapter;
    private HttpApplication application;
    private Factory<Buffer> storage;
    private Handler handler;

    @Override
    public void init() throws ServletException {
        adapter = getAdapter(getServletContext());
        application = new ServletHttpApplicationWrapper(getApplication(), getServletContext());
        storage = application.getBufferFactory();
        if (storage == null) {
            final File tmpDir = (File) getServletContext().getAttribute(SERVLET_TEMP_DIR);
            storage = newTemporaryStorage(tmpDir);
        }
        try {
            handler = application.start();
        } catch (HttpApplicationException e) {
            throw new ServletException("Failed to start HTTP Application", e);
        }
    }

    private ServletVersionAdapter getAdapter(ServletContext servletContext) throws ServletException {
        switch (servletContext.getMajorVersion()) {
        case 1:
            // FIXME: i18n.
            throw new ServletException("Unsupported Servlet version "
                    + servletContext.getMajorVersion());
        case 2:
            return new Servlet2Adapter();
        default:
            return new Servlet3Adapter();
        }
    }

    private HttpApplication getApplication() throws ServletException {
        ServiceLoader<HttpApplication> configurations = ServiceLoader.load(HttpApplication.class);
        Iterator<HttpApplication> iterator = configurations.iterator();

        if (!iterator.hasNext()) {
            throw new ServletException("No ServletConfiguration implementation registered.");
        }

        HttpApplication configuration = iterator.next();

        if (iterator.hasNext()) {
            // Multiple ServletConfigurations registered!
            List<Object> messageParams = new ArrayList<Object>();
            messageParams.add(iterator.next().getClass().getName());

            String message = "Multiple ServletConfiguration implementations registered.\n%d configurations found: %s";

            while (iterator.hasNext()) {
                messageParams.add(iterator.next().getClass().getName());
                message += ", %s";
            }
            messageParams.add(0, messageParams.size());

            throw new ServletException(String.format(message, messageParams.toArray()));
        }
        return configuration;
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        final Request request = createRequest(req);
        final Session session = new ServletSession(req);
        final HttpContext httpContext = new HttpContext(new RootContext(), session)
                .setPrincipal(req.getUserPrincipal());

        Enumeration<String> attributeNames = req.getAttributeNames(); //TODO add comment on why this was added as probably shouldn't stick around as only to fix AM's case of forwarding the request from a different servlet?....
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            httpContext.getAttributes().put(attributeName, req.getAttribute(attributeName));
        }

        //FIXME ideally we don't want to expose the HttpServlet Request and Response
        // handy servlet-specific attributes, sure to be abused by downstream filters
        httpContext.getAttributes().put(HttpServletRequest.class.getName(), req);
        httpContext.getAttributes().put(HttpServletResponse.class.getName(), resp);

        Context context = createRouterContext(createClientInfoContext(httpContext, req), req);

        // handle request
        final ServletSynchronizer sync = adapter.createServletSynchronizer(req, resp);
        try {
            final Promise<Response, ResponseException> promise = handler.handle(context, request)
                    .onSuccess(new SuccessHandler<Response>() {
                        @Override
                        public void handleResult(Response response) {
                            try {
                                writeResponse(httpContext, resp, response);
                            } catch (IOException e) {
                                log("Failed to write success response", e);
                            } finally {
                                closeSilently(request, response);
                                sync.signalAndComplete();
                            }
                        }
                    })
                    .onFailure(new FailureHandler<ResponseException>() {
                        @Override
                        public void handleError(ResponseException error) {
                            try {
                                writeResponse(httpContext, resp, error.getResponse());
                            } catch (IOException e) {
                                log("Failed to write success response", e);
                            } finally {
                                closeSilently(request, error.getResponse());
                                sync.signalAndComplete();
                            }
                        }
                    });

            sync.setAsyncListener(new Runnable() {
                @Override
                public void run() {
                    promise.cancel(true);
                    //TODO is this needed? Not sure if the latch would have been freed by now.
                    sync.signalAndComplete();
                }
            });

        } catch (ResponseException error) {
            try {
                writeResponse(httpContext, resp, error.getResponse());
            } catch (IOException e) {
                log("Failed to write success response", e);
            } finally {
                closeSilently(request, error.getResponse());
                sync.signalAndComplete();
            }
        }

        try {
            sync.awaitIfNeeded();
        } catch (InterruptedException e) {
            throw new ServletException("Awaiting asynchronous request was interrupted.", e);
        }
    }

    private Request createRequest(HttpServletRequest req) throws ServletException, IOException {
        // populate request
        Request request = new Request();
        request.setMethod(req.getMethod());
        try {
            request.setUri(URIUtil.create(req.getScheme(), null, req.getServerName(),
                    req.getServerPort(), req.getRequestURI(), req.getQueryString(), null));
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
            request.setEntity(newBranchingInputStream(req.getInputStream(), storage));
        }

        return request;
    }

    private ClientInfoContext createClientInfoContext(Context parent, HttpServletRequest req) {
        return ClientInfoContext.builder(parent)
                .remoteUser(req.getRemoteUser())
                .remoteAddress(req.getRemoteAddr())
                .remoteHost(req.getRemoteHost())
                .remotePort(req.getRemotePort())
                .certificates((X509Certificate[]) req.getAttribute(SERVLET_REQUEST_X509_ATTRIBUTE))
                .userAgent(req.getHeader("User-Agent"))
                .build();
    }

    private RouterContext createRouterContext(Context parent, HttpServletRequest req) {
        String contextPath = forceEmptyIfNull(req.getContextPath());
        contextPath = contextPath.startsWith("/") ? contextPath.substring(1) : contextPath;
        String matchedUri = contextPath + forceEmptyIfNull(req.getServletPath());
        if (ResourceName.valueOf(matchedUri).equals(ResourceName.valueOf(req.getRequestURI()))) {
            //Must be registered at '/' path
            matchedUri = contextPath;
        }
        return new RouterContext(parent, matchedUri, Collections.<String, String>emptyMap());
    }

    private String forceEmptyIfNull(final String s) {
        return s != null ? s : "";
    }

    private void writeResponse(HttpContext context, HttpServletResponse resp, Response response)
            throws IOException {
        /*
         * Support for OPENIG-94/95 - The wrapped servlet may have already
         * committed its response w/o creating a new OpenIG Response instance in
         * the exchange.
         */
        if (response != null) {
            // response status-code (reason-phrase deprecated in Servlet API)
            resp.setStatus(response.getStatus());

            // ensure that the session has been written back to the response
            context.getSession().save(response);

            // response headers
            for (String name : response.getHeaders().keySet()) {
                for (String value : response.getHeaders().get(name)) {
                    if (value != null && value.length() > 0) {
                        resp.addHeader(name, value);
                    }
                }
            }
            // response entity (if applicable)
            // TODO does this also set content length?
            response.getEntity().copyRawContentTo(resp.getOutputStream());
        }
    }

    @Override
    public void destroy() {
        application.stop();
    }
}
