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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Creates a static response with a generated DES key.
 *
 * <br>
 *
 * This handler takes no configuration settings.
 *
 * <pre>
 * {@code
 * {
 *   "name": "KeyGenerator",
 *   "type": "DesKeyGenHandler"
 * }
 * }
 * </pre>
 *
 * When called, it generates a base64-encoded DES key,
 * and returns the "key" value in a JSON response:
 *
 * <pre>{@code {"key":"/R/9khUxnaQ="}}</pre>
 *
 * If the handler fails to find a key generator for DES keys,
 * then it does not return a "key", but instead returns an "error":
 *
 * <pre>{@code {"error":"Failed to generate a key: ..."}}</pre>
 */
public class DesKeyGenHandler extends GenericHeapObject implements Handler {

    /**
     * Generate a base64-encoded DES key.
     *
     * @return  On success, a Map with key "key" whose value is the encoded key.
     *          On failure, a Map with key "error" whose value is an error message.
     */
    private Map<String, String> getSharedKey() {
        Map<String, String> sharedKey = new HashMap<>();
        try {
            KeyGenerator generator = KeyGenerator.getInstance("DES");
            SecretKey key = generator.generateKey();
            sharedKey.put("key", Base64.encode(key.getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            sharedKey.put("error", "Failed to generate a key: " + e.getMessage());
        }
        return sharedKey;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Response response = new Response(Status.OK);
        response.setEntity(getSharedKey());
        return Promises.newResultPromise(response);
    }

    /**
     * Creates and initializes a DES key generator handler in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            return new DesKeyGenHandler();
        }
    }
}
