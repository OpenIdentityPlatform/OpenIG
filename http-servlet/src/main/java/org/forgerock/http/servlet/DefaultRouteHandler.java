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
 * Portions Copyright Spring spring-framework/spring-webmvc/src/main/java/org/springframework/web/servlet/resource/DefaultServletHttpRequestHandler TODO
 */

package org.forgerock.http.servlet;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpContext;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.resource.core.Context;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * <p>{@code Handler} implementation to handle the default route for Servlet containers.</p>
 *
 * <p>This {@code Handler} should be used to handle requests to static resources.</p>
 *
 * @since 1.0.0
 */
final class DefaultRouteHandler implements Handler {

    /** Default Servlet name used by Tomcat, Jetty, JBoss, and GlassFish */
    private static final String COMMON_DEFAULT_SERVLET_NAME = "default";

    /** Default Servlet name used by Google App Engine */
    private static final String GAE_DEFAULT_SERVLET_NAME = "_ah_default";

    /** Default Servlet name used by Resin */
    private static final String RESIN_DEFAULT_SERVLET_NAME = "resin-file";

    /** Default Servlet name used by WebLogic */
    private static final String WEBLOGIC_DEFAULT_SERVLET_NAME = "FileServlet";

    /** Default Servlet name used by WebSphere */
    private static final String WEBSPHERE_DEFAULT_SERVLET_NAME = "SimpleFileServlet";

    private final ServletContext servletContext;
    private final String defaultServletName;
    private final Map<Pattern, String> staticRouteServlets;

    DefaultRouteHandler(ServletContext servletContext, String defaultServletName,
            Map<Pattern, String> staticRouteServlets) {
        this.servletContext = servletContext;
        this.staticRouteServlets = staticRouteServlets;
        if (defaultServletName == null || defaultServletName.isEmpty()) {
            if (this.servletContext.getNamedDispatcher(COMMON_DEFAULT_SERVLET_NAME) != null) {
                this.defaultServletName = COMMON_DEFAULT_SERVLET_NAME;
            } else if (this.servletContext.getNamedDispatcher(GAE_DEFAULT_SERVLET_NAME) != null) {
                this.defaultServletName = GAE_DEFAULT_SERVLET_NAME;
            } else if (this.servletContext.getNamedDispatcher(RESIN_DEFAULT_SERVLET_NAME) != null) {
                this.defaultServletName = RESIN_DEFAULT_SERVLET_NAME;
            } else if (this.servletContext.getNamedDispatcher(WEBLOGIC_DEFAULT_SERVLET_NAME) != null) {
                this.defaultServletName = WEBLOGIC_DEFAULT_SERVLET_NAME;
            } else if (this.servletContext.getNamedDispatcher(WEBSPHERE_DEFAULT_SERVLET_NAME) != null) {
                this.defaultServletName = WEBSPHERE_DEFAULT_SERVLET_NAME;
            } else {
                throw new IllegalStateException("Unable to locate the default servlet for serving static content. "
                        + "Please set the 'defaultServletName' property explicitly.");
            }
        } else {
            this.defaultServletName = defaultServletName;
        }
    }

    /**
     * Dispatches the request to be handled by a different Servlet.
     *
     * @param context {@inheritDoc}
     * @param request {@inheritDoc}
     * @return A successful promise with a {@code null} {@code Response} to signify that the container has already
     * handled the response or a Internal Server Error response if the request could not be dispatched.
     */
    @Override
    public Promise<Response, ResponseException> handle(Context context, Request request) {

        HttpContext httpContext = context.asContext(HttpContext.class);
        HttpServletRequest req =
                (HttpServletRequest) httpContext.getAttributes().get(HttpServletRequest.class.getName());
        HttpServletResponse resp =
                (HttpServletResponse) httpContext.getAttributes().get(HttpServletResponse.class.getName());

        String servletName = getServletName(request);
        RequestDispatcher dispatcher = servletContext.getNamedDispatcher(servletName);
        if (dispatcher == null) {
            throw new IllegalStateException("A RequestDispatcher could not be located for the default servlet '"
                    + servletName + "'");
        }
        try {
            dispatcher.forward(req, resp);
        } catch (ServletException e) {
            return Promises.newFailedPromise(
                    new ResponseException(new Response().setStatusAndReason(500), e.getMessage(), e));
        } catch (IOException e) {
            return Promises.newFailedPromise(
                    new ResponseException(new Response().setStatusAndReason(500), e.getMessage(), e));
        }

        // Returns null as the container has already handled the response.
        return Promises.newSuccessfulPromise(null);
    }

    private String getServletName(Request request) {
        String servletName = defaultServletName;
        for (Map.Entry<Pattern, String> staticRouteServlet : staticRouteServlets.entrySet()) {
            if (staticRouteServlet.getKey().matcher(request.getUri().getPath()).matches()) {
                servletName = staticRouteServlet.getValue();
            }
        }
        return servletName;
    }
}
