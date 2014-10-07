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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.http.servlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Servlet API version adapter provides an abstraction which allows Servlet
 * and Filter implementations to interact with the Servlet container
 * independently of the Servlet API version. At the moment the adapter only
 * provides an abstraction for performing asynchronous processing, but other API
 * features could be added in future (such as async IO).
 */
abstract class ServletApiVersionAdapter {

    /**
     * Returns an adapter configured for the current Servlet API version.
     *
     * @param servletContext
     *            The context.
     * @return An adapter appropriate for the Servlet container.
     * @throws ServletException
     *             If the Servlet container version is not supported.
     */
    public static ServletApiVersionAdapter getInstance(ServletContext servletContext)
            throws ServletException {
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

    /**
     * Prevent sub-classing and instantiation outside this package.
     */
    ServletApiVersionAdapter() {
        // Nothing to do.
    }

    /**
     * Creates a new synchronizer appropriate for the provided HTTP request.
     *
     * @param httpRequest
     *            The HTTP request.
     * @param httpResponse
     *            The HTTP response.
     * @return Returns a new synchronizer appropriate for the HTTP request.
     */
    public abstract ServletSynchronizer createServletSynchronizer(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse);
}
