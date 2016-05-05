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
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.openig.heap.Name.of;

import java.security.KeyStore;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class KeyStoreHeapletTest {

    @Test
    public void shouldLoadJksKeyStore() throws Exception {

        JsonValue config = json(object(
                field("url", resource("/keypair-keystore.jks")),
                field("password", "changeit")
        ));
        KeyStoreHeaplet heaplet = new KeyStoreHeaplet();
        KeyStore store = (KeyStore) heaplet.create(Name.of(getClass().getName()), config, buildDefaultHeap());

        assertThat(store.containsAlias("keypair")).isTrue();
        assertThat(store.getType()).isEqualToIgnoringCase("JKS");
    }

    @Test
    public void shouldLoadPkcs12KeyStore() throws Exception {
        JsonValue config = json(object(
                field("url", resource("/mykey-keystore.pkcs12")),
                field("password", "changeit"),
                field("type", "PKCS12")
        ));
        KeyStoreHeaplet heaplet = new KeyStoreHeaplet();
        KeyStore store = (KeyStore) heaplet.create(of(getClass().getName()), config, buildDefaultHeap());

        assertThat(store.containsAlias("mykey")).isTrue();
        assertThat(store.getType()).isEqualToIgnoringCase("PKCS12");
    }

    private String resource(final String name) {
        return getClass().getResource(name).toString();
    }

}
