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
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.forgerock.json.JsonValueFunctions.enumConstant;

/**
 * Secret store that retrieves secrets from environment variables and system properties.
 * Environment variables are checked first, with system properties as fallback.
 * <pre>
 * {
 *     "type": "SystemAndEnvSecretStore",
 *     "config": {
 *         "format": "PLAIN"
 *     }
 * }
 * </pre>
 */
public class SystemAndEnvSecretStore {

    private final Format format;

    public SystemAndEnvSecretStore(Format format) {
        this.format = format;
    }

    public byte[] getSecret(String id) {
        final String key = id.toUpperCase().replaceAll("\\.", "_");
        String value = System.getenv(key);

        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null) {
            return null;
        }

        return decodeValue(value);
    }
    private byte[] decodeValue(String value) {
        if (Format.BASE64.equals(format)) {
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Invalid Base64 encoding for secret value", e);
            }
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] getSecretFromHeap(Heap heap, String id) throws HeapException {
        List<SystemAndEnvSecretStore> secretStores = heap.getAll(SystemAndEnvSecretStore.class);

        return secretStores.stream()
                .map(secretStore -> secretStore.getSecret(id))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public enum Format {
        PLAIN,
        BASE64
    }

    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            JsonValue evaluatedConfig = config.as(evaluatedWithHeapProperties());
            Format format = evaluatedConfig.get("format")
                    .defaultTo("BASE64")
                    .as(enumConstant(Format.class));
            return new SystemAndEnvSecretStore(format);
        }
    }
}
