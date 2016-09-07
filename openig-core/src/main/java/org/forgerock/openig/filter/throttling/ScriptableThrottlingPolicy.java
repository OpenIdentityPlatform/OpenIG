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
package org.forgerock.openig.filter.throttling;

import static org.forgerock.openig.el.Bindings.bindings;

import javax.script.ScriptException;

import org.forgerock.http.filter.throttling.ThrottlingPolicy;
import org.forgerock.http.filter.throttling.ThrottlingRate;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.script.AbstractScriptableHeapObject;
import org.forgerock.openig.script.Script;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scriptable throttling datasource. This throttling datasource acts as a simple wrapper around the
 * scripting engine. Scripts are provided with the bindings provided by {@link AbstractScriptableHeapObject}.
 */
public class ScriptableThrottlingPolicy
        extends AbstractScriptableHeapObject<ThrottlingRate>
        implements ThrottlingPolicy {

    private static final Logger logger = LoggerFactory.getLogger(ScriptableThrottlingPolicy.class);

    ScriptableThrottlingPolicy(final Script compiledScript, final Heap heap, final String name) {
        super(compiledScript, heap, name);
    }

    @Override
    public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
        return runScript(bindings(context, request), context, ThrottlingRate.class)
                .thenCatch(wrapException());
    }

    private Function<ScriptException, ThrottlingRate, Exception> wrapException() {
        return new Function<ScriptException, ThrottlingRate, Exception>() {
            @Override
            public ThrottlingRate apply(ScriptException e) throws Exception {
                logger.warn("An error occurred in a throttling policy script", e);
                throw new Exception(e);
            }
        };
    }

    /**
     * Creates and initializes a scriptable object in a heap environment.
     */
    public static class Heaplet extends AbstractScriptableHeaplet {
        @Override
        public ScriptableThrottlingPolicy newInstance(Script script, Heap heap) throws HeapException {
            return new ScriptableThrottlingPolicy(script, heap, name);
        }
    }
}

