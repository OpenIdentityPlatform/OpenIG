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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Servlet API version adapter provides an abstraction which allows Servlet
 * and Filter implementations to interact with the Servlet container
 * independently of the Servlet API version. At the moment the adapter only
 * provides an abstraction for performing asynchronous processing, but other API
 * features could be added in future (such as async IO).
 *
 * @since 1.0.0
 */
interface ServletVersionAdapter {

    /**
     * Creates a new synchronizer appropriate for the provided HTTP request.
     *
     * @param httpRequest
     *            The HTTP request.
     * @param httpResponse
     *            The HTTP response.
     * @return Returns a new synchronizer appropriate for the HTTP request.
     */
    ServletSynchronizer createServletSynchronizer(HttpServletRequest httpRequest, HttpServletResponse httpResponse);
}
