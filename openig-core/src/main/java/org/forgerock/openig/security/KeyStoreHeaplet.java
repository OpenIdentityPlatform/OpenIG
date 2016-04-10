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

import static java.lang.String.format;
import static org.forgerock.json.JsonValueFunctions.url;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.util.Utils.closeSilently;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Represents a loaded Java {@link KeyStore}.
 * <pre>
 *     {@code
 *     {
 *         "name": "LocalKeyStore",
 *         "type": "KeyStore",
 *         "config": {
 *             "url": "file://${env['HOME']}/keystore.jks",
 *             "password": "secret",
 *             "type": "JKS"
 *         }
 *     }
 *     }
 * </pre>
 * <ul>
 *     <li>{@literal url}: URL to the target key store file (expression, required).</li>
 *     <li>{@literal type}: key store type (defaults to platform's default type) (string, optional).</li>
 *     <li>{@literal password}: credential required to read private keys from the key store (expression, optional),
 *     not needed when the key store is used for a trust store.</li>
 * </ul>
 * @since 3.1
 */
public class KeyStoreHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        JsonValue evaluated = config.as(evaluated());
        URL url = evaluated.get("url").required().as(url());
        String password = evaluated.get("password").asString();
        String type = evaluated.get("type").defaultTo(KeyStore.getDefaultType()).asString().toUpperCase();

        KeyStore keyStore = null;
        InputStream keyInput = null;
        try {
            keyStore = KeyStore.getInstance(type);
            keyInput = url.openStream();
            char[] credentials = (password == null) ? null : password.toCharArray();
            keyStore.load(keyInput, credentials);
        } catch (Exception e) {
            throw new HeapException(format("Cannot load %S KeyStore from %s", type, url.toString()), e);
        } finally {
            closeSilently(keyInput);
        }
        return keyStore;
    }
}
