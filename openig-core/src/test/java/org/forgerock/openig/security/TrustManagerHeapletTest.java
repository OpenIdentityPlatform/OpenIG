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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.TrustManager;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.log.LogSink;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TrustManagerHeapletTest {

    /**
     * Use a special heap object name to avoid the heaplet complaining about missing LogSink and TemporaryStorage heap
     * objects.
     */
    public static final String OBJECT_NAME = LogSink.LOGSINK_HEAP_KEY;

    @Test
    public void shouldLoadTrustManagerWithDefaultAlgorithm() throws Exception {
        HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        heap.put("KeyStore", loadKeyStore("jks", "/x509cert-keystore.jks"));

        JsonValue config = json(object(
                field("keystore", "KeyStore")
        ));

        TrustManagerHeaplet heaplet = new TrustManagerHeaplet();
        assertThat(heaplet.create(Name.of(OBJECT_NAME), config, heap))
                .isNotNull()
                .isInstanceOf(TrustManager.class);
    }

    private KeyStore loadKeyStore(String type, String name) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        InputStream is = getClass().getResourceAsStream(name);
        keyStore.load(is, null);
        return keyStore;
    }

}
