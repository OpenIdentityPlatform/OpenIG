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

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.routing.UriRouter;
import org.forgerock.util.Factory;

import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Wraps a {@link HttpApplication} instance and encapsulates Servlet specific application handling logic.
 *
 * @since 1.0.0
 */
final class ServletHttpApplicationWrapper implements HttpApplication {

    private final HttpApplication application;
    private final ServletContext servletContext;

    ServletHttpApplicationWrapper(HttpApplication application, ServletContext servletContext) {
        this.application = application;
        this.servletContext = servletContext;
    }

    /**
     * <p>Starts the Http Application by getting the root {@link Handler} that will handle all HTTP Requests.</p>
     *
     * <p>This method also performs extra processing that is specific to the application being deployed in a Servlet
     * container. If the wrapped {@code HttpApplication} is an instance of {@link ServletHttpApplication} then
     * the configured {@code Map} of static route handlers will be loaded. If the root {@code Handler} is an instance
     * of {@link UriRouter} then a default route will be set for the container to handler requests to static resource
     * using it's default Servlet and/or the {@code Map} of configured static route handlers.</p>
     *
     * @return {@inheritDoc}
     * @throws HttpApplicationException {@inheritDoc}
     */
    @Override
    public Handler start() throws HttpApplicationException {
        String defaultServletName = null;
        Map<Pattern, String> staticRouteServlets = Collections.emptyMap();
        if (application instanceof ServletHttpApplication) {
            ServletHttpApplication servletApplication = (ServletHttpApplication) application;
            defaultServletName = servletApplication.getDefaultServletName();
            staticRouteServlets = servletApplication.getStaticRouteServlets();
        }
        Handler handler = application.start();
        if (handler instanceof UriRouter) {
            UriRouter router = (UriRouter) handler;
            if (router.getDefaultRoute() == null) {
                router.setDefaultRoute(new DefaultRouteHandler(servletContext, defaultServletName,
                        staticRouteServlets));
            }
        }

        return handler;
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        return application.getBufferFactory();
    }

    @Override
    public void stop() {
        application.stop();
    }
}
