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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.secrets;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

public class SystemAndEnvSecretStoreTest {

    final static String SECRET_VALUE = "s3cr3t";


    @Test(description = "Should retrieve secret from system property in plain format")
    public void testGetSecretFromSystemPropertyPlain() {

        System.setProperty("TEST_SECRET", SECRET_VALUE);

        SystemAndEnvSecretStore store = new SystemAndEnvSecretStore(SystemAndEnvSecretStore.Format.PLAIN);

        byte[] result = store.getSecret("test.secret");

        assertThat(result).isNotEmpty();
        assertThat(SECRET_VALUE).isEqualTo(new String(result, StandardCharsets.UTF_8));
    }

    @Test
    public void testGetSecretFromSystemPropertyBase64() {
        String base64Value = Base64.getEncoder().encodeToString(SECRET_VALUE.getBytes(StandardCharsets.UTF_8));
        System.setProperty("API_KEY", base64Value);

        SystemAndEnvSecretStore store = new SystemAndEnvSecretStore(SystemAndEnvSecretStore.Format.BASE64);

        byte[] result = store.getSecret("api.key");

        assertThat(result).isNotEmpty();
        assertThat(SECRET_VALUE).isEqualTo(new String(result, StandardCharsets.UTF_8));
    }

    @Test
    public void createSecretStoreFromHeapTest() throws HeapException {

        HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        JsonValue config = json(object(field("format", "PLAIN")));
        SystemAndEnvSecretStore secrets = (SystemAndEnvSecretStore)
                new SystemAndEnvSecretStore.Heaplet()
                        .create(Name.of("this"), config, heap);

        assertThat(secrets).isNotNull();
    }

}