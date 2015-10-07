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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.security;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Trust all certificates that this class is asked to check.
 */
public class TrustAllManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(final X509Certificate[] certificates, final String authType)
            throws CertificateException { }

    @Override
    public void checkServerTrusted(final X509Certificate[] certificates, final String authType)
            throws CertificateException { }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * Creates and initializes a trust-all manager in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            logger.warning("Using TrustAllManager is not safe when deployed in production. "
                                   + "Declare the appropriate KeyStore and linked TrustManager(s) instead.");
            return new TrustAllManager();
        }
    }
}
