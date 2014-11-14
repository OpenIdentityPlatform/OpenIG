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

package org.forgerock.http;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * ClientInfo gives easy access to client-related information that are available into the request.
 * Supported data includes:
 * <ul>
 *     <li>Remote IP address or hostname</li>
 *     <li>Remote port</li>
 *     <li>Username</li>
 *     <li>Client provided X509 certificates</li>
 *     <li>User-Agent information</li>
 * </ul>
 */
public interface ClientInfo {

    /**
     * Returns the login of the user making this request or {@code null} if not known.
     *
     * @return the login of the user making this request or {@code null} if not known.
     */
    String getRemoteUser();

    /**
     * Returns the IP address of the client (or last proxy) that sent the request.
     *
     * @return the IP address of the client (or last proxy) that sent the request.
     */
    String getRemoteAddress();

    /**
     * Returns the fully qualified name of the client (or last proxy) that sent the request.
     *
     * @return the fully qualified name of the client (or last proxy) that sent the request.
     */
    String getRemoteHost();

    /**
     * Returns the source port of the client (or last proxy) that sent the request.
     *
     * @return the source port of the client (or last proxy) that sent the request.
     */
    int getRemotePort();

    /**
     * Returns the list (possibly empty) of X509 certificate(s) provided by the client.
     * If no certificates are available, an empty list is returned.
     *
     * @return the list (possibly empty) of X509 certificate(s) provided by the client.
     */
    List<X509Certificate> getCertificates();

    /**
     * Returns the value of the {@literal User-Agent} HTTP Header (if any, returns {@code null} otherwise).
     *
     * @return the value of the {@literal User-Agent} HTTP Header (if any, returns {@code null} otherwise).
     */
    String getUserAgent();
}
