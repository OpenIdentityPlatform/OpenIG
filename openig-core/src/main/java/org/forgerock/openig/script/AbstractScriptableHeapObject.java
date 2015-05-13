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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.openig.script;

import static org.forgerock.openig.config.Environment.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.http.HttpClient.HTTP_CLIENT_HEAP_KEY;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Adapters;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.http.Responses;
import org.forgerock.openig.ldap.LdapClient;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * An abstract scriptable heap object which should be used as the base class for
 * implementing {@link org.forgerock.http.Filter filters} and {@link Handler handlers}. This heap
 * object acts as a simple wrapper around the scripting engine. Scripts are
 * provided with the following variable bindings:
 * <ul>
 * <li>{@link Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link HttpClient http} - an OpenIG HTTP client which may be used for
 * performing outbound HTTP requests
 * <li>{@link LdapClient ldap} - an OpenIG LDAP client which may be used for
 * performing LDAP requests such as LDAP authentication
 * <li>{@link org.forgerock.openig.log.Logger logger} - the OpenIG logger
 * <li>{@link Handler next} - if the heap object is a filter then this variable
 * will contain the next handler in the filter chain.
 * </ul>
 * <p>
 * <b>NOTE:</b> at the moment only Groovy is supported.
 */
public abstract class AbstractScriptableHeapObject extends GenericHeapObject {

    /** Creates and initializes a capture filter in a heap environment. */
    protected abstract static class AbstractScriptableHeaplet extends GenericHeaplet {
        private static final String CONFIG_OPTION_FILE = "file";
        private static final String CONFIG_OPTION_SOURCE = "source";
        private static final String CONFIG_OPTION_TYPE = "type";
        private static final String CONFIG_OPTION_ARGS = "args";

        @Override
        public Object create() throws HeapException {
            final Script script = compileScript();
            final AbstractScriptableHeapObject component = newInstance(script);
            HttpClient httpClient = heap.resolve(config.get("httpClient").defaultTo(HTTP_CLIENT_HEAP_KEY),
                                                 HttpClient.class);
            component.setHttpClient(httpClient);
            if (config.isDefined(CONFIG_OPTION_ARGS)) {
                component.setArgs(config.get(CONFIG_OPTION_ARGS).asMap());
            }
            return component;
        }

        /**
         * Creates the new heap object instance using the provided script.
         *
         * @param script The compiled script.
         * @return The new heap object instance using the provided script.
         * @throws HeapException if an exception occurred during creation of the heap
         * object or any of its dependencies.
         * @throws JsonValueException if the heaplet (or one of its dependencies) has a
         * malformed configuration.
         */
        protected abstract AbstractScriptableHeapObject newInstance(final Script script)
                throws HeapException;

        private final Script compileScript() throws HeapException {
            final Environment environment = heap.get(ENVIRONMENT_HEAP_KEY, Environment.class);

            if (!config.isDefined(CONFIG_OPTION_TYPE)) {
                throw new JsonValueException(config, "The configuration option '"
                        + CONFIG_OPTION_TYPE
                        + "' is required and must specify the script mime-type");
            }
            final String mimeType = config.get(CONFIG_OPTION_TYPE).asString();
            if (config.isDefined(CONFIG_OPTION_SOURCE)) {
                if (config.isDefined(CONFIG_OPTION_FILE)) {
                    throw new JsonValueException(config, "Both configuration options '"
                            + CONFIG_OPTION_SOURCE + "' and '" + CONFIG_OPTION_FILE
                            + "' were specified, when at most one is allowed");
                }
                final String source = config.get(CONFIG_OPTION_SOURCE).asString();
                try {
                    return Script.fromSource(environment, mimeType, source);
                } catch (final ScriptException e) {
                    throw new JsonValueException(config,
                            "Unable to compile the script defined in '" + CONFIG_OPTION_SOURCE
                                    + "'", e
                    );
                }
            } else if (config.isDefined(CONFIG_OPTION_FILE)) {
                final String script = config.get(CONFIG_OPTION_FILE).asString();
                try {
                    return Script.fromFile(environment, mimeType, script);
                } catch (final ScriptException e) {
                    throw new JsonValueException(config, "Unable to compile the script in file '"
                            + script + "'", e);
                }
            } else {
                throw new JsonValueException(config, "Neither of the configuration options '"
                        + CONFIG_OPTION_SOURCE + "' and '" + CONFIG_OPTION_FILE
                        + "' were specified");
            }
        }

    }

    // TODO: add support for periodically refreshing the Groovy script file.
    // TODO: json/xml/sql/crest bindings.

    private final Script compiledScript;
    private HttpClient httpClient;
    private final LdapClient ldapClient = LdapClient.getInstance();
    private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<String, Object>();
    private Map<String, Object> args;

    /**
     * Creates a new scriptable heap object using the provided compiled script.
     *
     * @param compiledScript The compiled script.
     */
    protected AbstractScriptableHeapObject(final Script compiledScript) {
        this.compiledScript = compiledScript;
    }

    /**
     * Sets the HTTP client which should be made available to scripts.
     *
     * @param client The HTTP client which should be made available to scripts.
     */
    public void setHttpClient(final HttpClient client) {
        this.httpClient = client;
    }

    /**
     * Sets the parameters which should be made available to scripts.
     *
     * @param args The parameters which should be made available to scripts.
     */
    public void setArgs(final Map<String, Object> args) {
        this.args = args;
    }

    /**
     * Runs the compiled script using the provided exchange and optional
     * forwarding handler.
     *
     * @param exchange The HTTP exchange.
     * @param request the HTTP request
     * @param next The next handler in the chain if applicable, may be
     * {@code null}.
     * @return the Promise of a Response produced by the script
     */
    protected final Promise<Response, NeverThrowsException> runScript(final Exchange exchange,
                                                                      final Request request,
                                                                      final Handler next) {
        try {
            // TODO Do we need to force update exchange.request ?
            exchange.request = request;
            compiledScript.run(createBindings(exchange, next));
            return Promises.newResultPromise(exchange.response);
        } catch (final ScriptException e) {
            return Promises.newResultPromise(Responses.newInternalServerError("Cannot execute script", e));
        }
    }

    private Map<String, Object> createBindings(final Exchange exchange, final Handler next) {
        // Set engine bindings.
        final Map<String, Object> bindings = new HashMap<String, Object>();
        bindings.put("exchange", exchange);
        bindings.put("logger", logger);
        bindings.put("globals", scriptGlobals);
        if (httpClient != null) {
            bindings.put("http", httpClient);
        }
        bindings.put("ldap", ldapClient);
        if (next != null) {
            // FIXME For compatibity reasons, we still offer IG's Handler to scripts
            bindings.put("next", Adapters.asHandler(next));
        }
        if (args != null) {
            for (final Entry<String, Object> entry : args.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue());
            }
        }

        // Redirect streams? E.g. in = request entity, out = response entity?
        return bindings;
    }
}
