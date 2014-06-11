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
package org.forgerock.openig.filter;

import java.io.IOException;
import java.util.Map;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.ldap.LdapClient;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.script.AbstractScriptableHeapObject;
import org.forgerock.openig.script.Script;

/**
 * A scriptable filter. This filter acts as a simple wrapper around the
 * scripting engine. Scripts are provided with the following variable bindings:
 * <ul>
 * <li>{@link Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link HttpClient http} - an OpenIG HTTP client which may be used for
 * performing outbound HTTP requests
 * <li>{@link LdapClient ldap} - an OpenIG LDAP client which may be used for
 * performing LDAP requests such as LDAP authentication
 * <li>{@link Logger logger} - the OpenIG logger
 * <li>{@link Handler next} - the next handler in the filter chain.
 * </ul>
 * Like Java based filters, scripts are free to choose whether or not they
 * forward the request to the next handler or, instead, return a response
 * immediately.
 * <p/>
 * <b>NOTE:</b> at the moment only Groovy is supported.
 */
public class ScriptableFilter extends AbstractScriptableHeapObject implements Filter {

    /**
     * Creates and initializes a scriptable filter in a heap environment.
     */
    public static class Heaplet extends AbstractScriptableHeaplet {
        @Override
        public ScriptableFilter newInstance(Script script) throws HeapException {
            return new ScriptableFilter(script);
        }
    }

    ScriptableFilter(final Script compiledScript) {
        super(compiledScript);
    }

    @Override
    public void filter(final Exchange exchange, final Handler next) throws HandlerException,
            IOException {
        // Delegates filtering to the script.
        runScript(exchange, next);
    }
}
