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
package org.forgerock.openig.handler;

import java.io.IOException;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.groovy.AbstractGroovyHeapObject;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.Logger;

/**
 * A scriptable handler for the Groovy language. This handler acts as a simple
 * wrapper around the scripting engine. Scripts are provided with the following
 * variable bindings:
 * <ul>
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link Logger logger} - the OpenIG logger for this filter.
 * </ul>
 */
public class GroovyScriptHandler extends AbstractGroovyHeapObject implements Handler {

    /**
     * Creates and initializes a Groovy handler in a heap environment.
     */
    public static class Heaplet extends AbstractGroovyHeaplet {
        @Override
        public Object create() throws HeapException, JsonValueException {
            return new GroovyScriptHandler(compile("script"));
        }
    }

    // For unit testing.
    GroovyScriptHandler(final String... scriptLines) throws ScriptException {
        super(scriptLines);
    }

    private GroovyScriptHandler(final CompiledScript compiledScript) {
        super(compiledScript);
    }

    /**
     * Delegates handling to the Groovy script.
     */
    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        runScript(exchange, null);
    }
}
