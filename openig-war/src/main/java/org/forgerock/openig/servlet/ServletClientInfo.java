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

package org.forgerock.openig.servlet;

import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.security.cert.X509Certificate;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.forgerock.openig.http.ClientInfo;

/**
 * ServletClientInfo adapts an {@link HttpServletRequest} instance to the {@link ClientInfo} interface.
 */
public class ServletClientInfo implements ClientInfo {

    /**
     * Standard specified request attribute name for retrieving X509 Certificates.
     */
    private static final String SERVLET_REQUEST_X509_ATTRIBUTE = "javax.servlet.request.X509Certificate";

    private final HttpServletRequest request;

    /**
     * Builds a ServletClientInfo wrapping the given request.
     *
     * @param request
     *         adapted servlet request
     */
    public ServletClientInfo(final HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public String getRemoteAddress() {
        return request.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        return request.getRemoteHost();
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }

    @Override
    public List<X509Certificate> getCertificates() {
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute(SERVLET_REQUEST_X509_ATTRIBUTE);
        if (certificates != null) {
            return asList(certificates);
        }
        return emptyList();
    }

    @Override
    public String getUserAgent() {
        return request.getHeader("User-Agent");
    }

}
