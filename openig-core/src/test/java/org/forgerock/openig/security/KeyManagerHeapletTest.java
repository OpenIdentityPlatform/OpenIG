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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.X509KeyManager;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class KeyManagerHeapletTest {

    @Test
    public void shouldLoadKeyManagerFactoryWithDefaultAlgorithm() throws Exception {
        HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("KeyStore", loadKeyStore("jks", "/x509cert-keystore.jks", "changeit"));

        JsonValue config = json(object(
                field("keystore", "KeyStore"),
                field("password", "changeit")
        ));
        KeyManagerHeaplet heaplet = new KeyManagerHeaplet();
        X509KeyManager manager = (X509KeyManager) heaplet.create(Name.of("KeyManagerHeapletTest"), config, heap);

        assertThat(manager.getPrivateKey("cert")).isNotNull();
        assertThat(manager.getPrivateKey("cert").getFormat()).isEqualTo("PKCS#8");
        assertThat(manager.getCertificateChain("cert")).hasSize(1);
    }

    private KeyStore loadKeyStore(String type, String name, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        InputStream is = getClass().getResourceAsStream(name);
        char[] credentials = password.toCharArray();
        keyStore.load(is, credentials);
        return keyStore;
    }

}
