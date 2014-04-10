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
package org.forgerock.openig.script;

import static org.forgerock.util.Utils.joinAsString;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.forgerock.json.fluent.JsonException;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.ldap.LdapClient;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;

/**
 * An abstract scriptable heap object which should be used as the base class for
 * implementing {@link Filter filters} and {@link Handler handlers}. This heap
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
 * <li>{@link Logger logger} - the OpenIG logger
 * <li>{@link Handler next} - if the heap object is a filter then this variable
 * will contain the next handler in the filter chain.
 * </ul>
 * <p>
 * <b>NOTE:</b> at the moment only Groovy is supported.
 */
public abstract class AbstractScriptableHeapObject extends GenericHeapObject {

    /** Creates and initializes a capture filter in a heap environment. */
    protected static abstract class AbstractScriptableHeaplet extends NestedHeaplet {
        private static final String CONFIG_OPTION_TYPE = "type";
        private static final String CONFIG_OPTION_SOURCE = "source";
        private static final String CONFIG_OPTION_FILE = "file";

        @Override
        public Object create() throws HeapException, JsonValueException {
            CompiledScript script = compileScript();
            AbstractScriptableHeapObject component = newInstance(script);
            component.setHttpClient(new HttpClient(storage)); // TODO more config?
            return component;
        }

        /**
         * Creates the new heap object instance using the provided script.
         *
         * @param script
         *            The compiled script.
         * @return The new heap object instance using the provided script.
         * @throws HeapException
         *             if an exception occurred during creation of the heap
         *             object or any of its dependencies.
         * @throws JsonValueException
         *             if the heaplet (or one of its dependencies) has a
         *             malformed configuration.
         */
        protected abstract AbstractScriptableHeapObject newInstance(final CompiledScript script)
                throws HeapException, JsonValueException;

        private final CompiledScript compileScript() {
            if (!config.isDefined(CONFIG_OPTION_TYPE)) {
                throw new JsonException("The configuration option '" + CONFIG_OPTION_TYPE
                        + "' is required and must specify the script mime-type");
            }
            final String mimeType = config.get(CONFIG_OPTION_TYPE).asString();
            if (config.isDefined(CONFIG_OPTION_SOURCE)) {
                if (config.isDefined(CONFIG_OPTION_FILE)) {
                    throw new JsonException("Both configuration options '" + CONFIG_OPTION_SOURCE
                            + "' and '" + CONFIG_OPTION_FILE
                            + "' were specified, when at most one is allowed");
                }
                final String source = config.get(CONFIG_OPTION_SOURCE).asString();
                try {
                    return Scripts.compileSource(mimeType, source);
                } catch (final ScriptException e) {
                    throw new JsonException("Unable to compile the script defined in '"
                            + CONFIG_OPTION_SOURCE + "'", e);
                }
            } else if (config.isDefined(CONFIG_OPTION_FILE)) {
                final String script = config.get(CONFIG_OPTION_FILE).asString();
                try {
                    return Scripts.compileScript(mimeType, script);
                } catch (final ScriptException e) {
                    throw new JsonException(
                            "Unable to compile the script in file '" + script + "'", e);
                } catch (final FileNotFoundException e) {
                    throw new JsonException("Unable to read the script in file '" + script + "'", e);
                }
            } else {
                throw new JsonException("Neither of the configuration options '"
                        + CONFIG_OPTION_SOURCE + "' and '" + CONFIG_OPTION_FILE
                        + "' were specified");
            }
        }

    }

    // TODO: add support for periodically refreshing the Groovy script file.
    // TODO: json/xml/sql/crest bindings.

    private static final String EOL = System.getProperty("line.separator");
    private final CompiledScript compiledScript;
    private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<String, Object>();
    private HttpClient httpClient;
    private final LdapClient ldapClient = LdapClient.getInstance();

    /**
     * Creates a new scriptable heap object using the provided compiled script.
     *
     * @param compiledScript
     *            The compiled script.
     */
    protected AbstractScriptableHeapObject(final CompiledScript compiledScript) {
        this.compiledScript = compiledScript;
    }

    /**
     * Creates a new scriptable heap object using the provided lines of script.
     * This constructed is intended for unit tests.
     *
     * @param mimeType
     *            The script mime-type, e.g. "application/x-groovy".
     * @param scriptLines
     *            The lines of script.
     * @throws ScriptException
     *             If the script cannot be compiled.
     */
    protected AbstractScriptableHeapObject(String mimeType, final String... scriptLines)
            throws ScriptException {
        this.compiledScript =
                Scripts.compileSource(mimeType,
                        joinAsString(EOL, (Object[]) scriptLines));
    }

    /**
     * Sets the HTTP client which should be made available to scripts.
     *
     * @param client
     *            The HTTP client which should be made available to scripts.
     */
    public void setHttpClient(HttpClient client) {
        this.httpClient = client;
    }

    /**
     * Runs the compiled script using the provided exchange and optional
     * forwarding handler.
     *
     * @param exchange
     *            The HTTP exchange.
     * @param next
     *            The next handler in the chain if applicable, may be
     *            {@code null}.
     * @throws HandlerException
     *             If an error occurred while evaluating the script.
     * @throws IOException
     *             If an I/O exception occurs.
     */
    protected final void runScript(final Exchange exchange, final Handler next)
            throws HandlerException, IOException {
        final LogTimer timer = logger.getTimer().start();
        try {
            compiledScript.eval(scriptContext(compiledScript.getEngine(), exchange, next));
        } catch (final ScriptException e) {
            if (e.getCause() instanceof HandlerException) {
                /*
                 * This may result from invoking the next handler (for filters),
                 * or it may have been generated intentionally by the script.
                 * Either way, just pass it back up the chain.
                 */
                throw (HandlerException) e.getCause();
            }

            /*
             * The exception was unintentional: we could throw the cause or the
             * script exception. Let's throw the script exception because it may
             * contain useful line number information.
             */
            throw new HandlerException("Script failed unexpectedly", e);
        } finally {
            timer.stop();
        }
    }

    private ScriptContext scriptContext(final ScriptEngine engine, final Exchange exchange,
            final Handler next) {
        final SimpleScriptContext scriptContext = new SimpleScriptContext();

        // Copy global bindings.
        final ScriptContext globalContext = engine.getContext();
        scriptContext.setBindings(globalContext.getBindings(ScriptContext.GLOBAL_SCOPE),
                ScriptContext.GLOBAL_SCOPE);

        // Set engine bindings.
        final SimpleBindings bindings = new SimpleBindings();
        bindings.put("exchange", exchange);
        bindings.put("logger", logger);
        bindings.put("globals", scriptGlobals);
        if (httpClient != null) {
            bindings.put("http", httpClient);
        }
        bindings.put("ldap", ldapClient);
        if (next != null) {
            bindings.put("next", next);
        }
        scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        // Redirect streams? E.g. in = request entity, out = response entity?
        return scriptContext;
    }
}
