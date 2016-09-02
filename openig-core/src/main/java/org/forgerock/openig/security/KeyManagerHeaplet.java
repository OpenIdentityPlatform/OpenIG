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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.security;

import static java.lang.String.*;
import static org.forgerock.openig.util.JsonValues.*;

import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an SSL Java {@link KeyManager}.
 * <pre>
 *     {@code
 *     {
 *         "name": "MyKeyManager",
 *         "type": "KeyManager",
 *         "config": {
 *             "keystore": "MyKeyStore",
 *             "password": "secret",
 *             "alg": "SunX509"
 *         }
 *     }
 *     }
 * </pre>
 * <ul>
 *     <li>{@literal keystore}: Reference a KeyStore heap object (string, required).</li>
 *     <li>{@literal password}: credential required to read private keys from the key store (expression, required).</li>
 *     <li>{@literal alg}: key manager algorithm (defaults to platform's default type) (string, optional).</li>
 * </ul>
 * @since 3.1
 */
public class KeyManagerHeaplet extends GenericHeaplet {

    private static final Logger logger = LoggerFactory.getLogger(KeyManagerHeaplet.class);

    @Override
    public Object create() throws HeapException {
        JsonValue storeRef = config.get("keystore").required();
        KeyStore keyStore = storeRef.as(requiredHeapObject(heap, KeyStore.class));
        String password = config.get("password").as(evaluatedWithHeapProperties()).required().asString();
        String algorithm = config.get("alg")
                                 .as(evaluatedWithHeapProperties())
                                 .defaultTo(KeyManagerFactory.getDefaultAlgorithm())
                                 .asString();

        // Initialize a KeyManagerFactory
        KeyManagerFactory factory;
        try {
            factory = KeyManagerFactory.getInstance(algorithm);
            factory.init(keyStore, password.toCharArray());
        } catch (Exception e) {
            throw new HeapException(loadingError(algorithm, storeRef), e);
        }

        // Retrieve manager
        KeyManager[] managers = factory.getKeyManagers();
        if (managers.length == 1) {
            return managers[0];
        } else if (managers.length > 1) {
            logger.warn("Only the first KeyManager will be selected");
            return managers[0];
        }
        throw new HeapException(loadingError(algorithm, storeRef));
    }

    private String loadingError(final String algorithm, final JsonValue reference) {
        return format("Cannot build KeyManager[alg:%s] from KeyStore %s",
                      algorithm,
                      reference.as(heapObjectNameOrPointer()));
    }
}
