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
package org.forgerock.openig.groovy;

import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.Utils.joinAsString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
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
 * A abstract scriptable heap object for the Groovy language which should be
 * used as the base class for implementing {@link Filter filters} and
 * {@link Handler handlers}. This heap object acts as a simple wrapper around
 * the scripting engine. Scripts are provided with the following variable
 * bindings:
 * <ul>
 * <li>{@link Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link HttpClient http} - an OpenIG HTTP client which may be used for
 * performing outbound HTTP requests
 * <li>{@link Logger logger} - the OpenIG logger
 * <li>{@link Handler next} - if the heap object is a filter then this variable
 * will contain the next handler in the filter chain.
 * </ul>
 */
public abstract class AbstractGroovyHeapObject extends GenericHeapObject {

    /** Creates and initializes a capture filter in a heap environment. */
    protected static abstract class AbstractGroovyHeaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException, JsonValueException {
            CompiledScript script = compileScript();
            AbstractGroovyHeapObject component = newInstance(script);
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
        protected abstract AbstractGroovyHeapObject newInstance(final CompiledScript script)
                throws HeapException, JsonValueException;

        private final CompiledScript compileScript() {
            final String script = "script";
            final String scriptFile = script + "File";
            if (config.isDefined(script)) {
                if (config.isDefined(scriptFile)) {
                    throw new JsonException("Both " + script + " and " + scriptFile
                            + " were specified, when at most one is allowed");
                }
                try {
                    final ScriptEngine engine = getGroovyScriptEngine();
                    return ((Compilable) engine).compile(config.get(script).asString());
                } catch (final ScriptException e) {
                    throw new JsonException("Unable to compile the Groovy script defined in '"
                            + script + "'", e);
                }
            } else if (config.isDefined(scriptFile)) {
                final File f = config.get(scriptFile).asFile();
                FileReader reader = null;
                try {
                    reader = new FileReader(f);
                    final ScriptEngine engine = getGroovyScriptEngine();
                    return ((Compilable) engine).compile(reader);
                } catch (final ScriptException e) {
                    throw new JsonException("Unable to compile the Groovy script in file '" + f
                            + "'", e);
                } catch (final FileNotFoundException e) {
                    throw new JsonException("Unable to read the Groovy script in file '"
                            + f.getAbsolutePath() + "'", e);
                } finally {
                    closeSilently(reader);
                }
            } else {
                return null; // No script defined.
            }
        }

    }

    // TODO: add support for periodically refreshing the Groovy script file.
    // TODO: json/xml/sql/crest bindings.

    private static final String EOL = System.getProperty("line.separator");
    private static final ScriptEngineManager factory = new ScriptEngineManager();
    private final ScriptEngine engine;
    private final CompiledScript compiledScript;
    private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<String, Object>();
    private HttpClient httpClient;
    private final LdapClient ldapClient = LdapClient.getInstance();

    /**
     * Creates a new Groovy heap object using the provided Groovy compiled
     * script.
     *
     * @param compiledScript
     *            The compiled Groovy script.
     */
    protected AbstractGroovyHeapObject(final CompiledScript compiledScript) {
        this.engine = compiledScript.getEngine();
        this.compiledScript = compiledScript;
    }

    /**
     * Creates a new Groovy heap object using the provided lines of Groovy
     * script. This constructed is intended for unit tests.
     *
     * @param scriptLines
     *            The lines of Groovy script.
     * @throws ScriptException
     *             If the script cannot be compiled.
     */
    protected AbstractGroovyHeapObject(final String... scriptLines) throws ScriptException {
        this.engine = getGroovyScriptEngine();
        this.compiledScript =
                ((Compilable) engine).compile(joinAsString(EOL, (Object[]) scriptLines));
    }

    /**
     * Returns the Groovy scripting engine and bootstraps language specific
     * bindings.
     */
    private static ScriptEngine getGroovyScriptEngine() throws ScriptException {
        final ScriptEngine engine = factory.getEngineByName("groovy");

        /*
         * Make LDAP attributes properties of an LDAP entry so that they can be
         * accessed using the dot operator.
         */
        engine.eval("org.forgerock.opendj.ldap.Entry.metaClass.getProperty ="
                + "{ propertyName -> delegate.getAttribute(propertyName) }");

        return engine;
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
     * Runs the compiled Groovy script using the provided exchange and optional
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
            compiledScript.eval(scriptContext(exchange, next));
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
            throw new HandlerException("Groovy script failed unexpectedly", e);
        } finally {
            timer.stop();
        }
    }

    private ScriptContext scriptContext(final Exchange exchange, final Handler next) {
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
