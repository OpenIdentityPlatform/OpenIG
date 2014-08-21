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

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.script.AbstractScriptableHeapObject;
import org.forgerock.openig.script.Script;

/**
 * A scriptable handler. This handler acts as a simple wrapper around the
 * scripting engine. Scripts are provided with the following variable bindings:
 * <ul>
 * <li>{@link java.util.Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link org.forgerock.openig.http.HttpClient http} - an OpenIG HTTP client which may be used for
 * performing outbound HTTP requests
 * <li>{@link org.forgerock.openig.ldap.LdapClient ldap} - an OpenIG LDAP client which may be used for
 * performing LDAP requests such as LDAP authentication
 * <li>{@link org.forgerock.openig.log.Logger logger} - the OpenIG logger.
 * </ul>
 * <p>
 * <b>NOTE:</b> at the moment only Groovy is supported.
 */
public class ScriptableHandler extends AbstractScriptableHeapObject implements Handler {

    /**
     * Creates and initializes a scriptable handler in a heap environment.
     */
    public static class Heaplet extends AbstractScriptableHeaplet {
        @Override
        public ScriptableHandler newInstance(Script script) throws HeapException {
            return new ScriptableHandler(script);
        }
    }

    ScriptableHandler(final Script compiledScript) {
        super(compiledScript);
    }

    @Override
    public void handle(final Exchange exchange) throws HandlerException, IOException {
        runScript(exchange, null);
    }
}
