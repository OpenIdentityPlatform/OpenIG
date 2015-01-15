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

import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.http.HttpApplication;

/**
 * <p>Servlet specific configuration class to configure the {@code HttpApplication} instance.</p>
 *
 * <p>It is preferred to use the {@link HttpApplication} interface over this implementation specific interface, as
 * this ties the application to being run in a Servlet container.</p>
 *
 * @see HttpApplication
 * @since 1.0.0
 */
public interface ServletHttpApplication extends HttpApplication {

    /**
     * <p>Gets the name of the Servlet that handles all default routes for the container.</p>
     *
     * <p>Only set if differs from the default for Tomcat, Jetty, GlassFish, Google App Engine, Resin, WebLogic or
     * WebSphere default Servlet.</p>
     *
     * @return The name of the container's default Servlet.
     */
    String getDefaultServletName();

    /**
     * <p>Gets the static route Servlets for specific paths.</p>
     *
     * <p>Maps routes to specific static resource and the name of the Servlet that will handle requests for them. By
     * default the container's default Servlet will handle all requests to static resources, use this method to
     * override this behaviour.</p>
     *
     * @return A {@code Map} of static route Servlet.
     */
    Map<Pattern, String> getStaticRouteServlets();
}
