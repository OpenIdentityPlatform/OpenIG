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

package org.forgerock.openig.security;

import static java.lang.String.*;

import java.security.KeyStore;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Represents an SSL Java {@link TrustManager}.
 * <pre>
 *     {
 *         "name": "MyTrustManager",
 *         "type": "TrustManager",
 *         "config": {
 *             "keystore": "MyKeyStore",
 *             "alg": "SunX509"
 *         }
 *     }
 * </pre>
 * <ul>
 *     <li>{@literal keystore}: Reference a KeyStore heap object (string, required).</li>
 *     <li>{@literal alg}: Trust manager algorithm (defaults to platform's default type) (string, optional).</li>
 * </ul>
 * @since 3.1
 */
public class TrustManagerHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        JsonValue storeRef = config.get("keystore").required();
        KeyStore keyStore = heap.resolve(storeRef, KeyStore.class);
        String algorithm = config.get("alg").defaultTo(TrustManagerFactory.getDefaultAlgorithm()).asString();

        TrustManagerFactory factory;
        try {
            factory = TrustManagerFactory.getInstance(algorithm);
            factory.init(keyStore);
        } catch (Exception e) {
            throw new HeapException(loadingError(algorithm, storeRef), e);
        }

        // Retrieve manager
        TrustManager[] managers = factory.getTrustManagers();
        if (managers.length == 1) {
            return managers[0];
        } else if (managers.length > 1) {
            logger.warning("Only the first TrustManager will be selected");
            return managers[0];
        }
        throw new HeapException(loadingError(algorithm, storeRef));

    }

    private String loadingError(final String algorithm, final JsonValue reference) {
        return format("Cannot build TrustManager[alg:%s] from KeyStore %s",
                      algorithm,
                      reference.asString());
    }
}
