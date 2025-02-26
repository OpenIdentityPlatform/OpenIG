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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2;

import static org.forgerock.openig.el.Bindings.bindings;

import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.script.AbstractScriptableHeapObject;
import org.forgerock.openig.script.Script;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * A Scriptable access token resolver. This access token resolver acts as a simple wrapper around the
 * scripting engine. Scripts are provided with the bindings provided by {@link AbstractScriptableHeapObject}.
 */
public class ScriptableAccessTokenResolver extends AbstractScriptableHeapObject<AccessTokenInfo>
        implements AccessTokenResolver {

    JsonValue config;

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(Context context, String token) {
        return runScript(
                bindings(context).bind("token", token).bind("config", this.config),
                context, AccessTokenInfo.class)
                .thenCatch(e -> {
                    throw new AccessTokenException("Error while resolving the access token", e);
                });
    }

    /**
     * Creates and initializes a scriptable access token resolver in a heap environment.
     */
    public static class Heaplet extends AbstractScriptableHeaplet {
        @Override
        public ScriptableAccessTokenResolver newInstance(Script script, Heap heap) throws HeapException {
            return new ScriptableAccessTokenResolver(script, heap, name);
        }
    }

    ScriptableAccessTokenResolver(final Script compiledScript, final Heap heap, final String name) {
        super(compiledScript, heap, name);
    }
    void setConfig(JsonValue config) {
        this.config = config;
    }
}
