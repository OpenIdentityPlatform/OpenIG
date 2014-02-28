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
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;

/**
 * A abstract scriptable heap object for the Groovy language which should be
 * used as the base class for implementing {@link Filter filters} and
 * {@link Handler handlers}. This heap object acts as a simple wrapper around
 * the scripting engine. Scripts are provided with the following variable
 * bindings:
 * <ul>
 * <li>{@link Exchange exchange} - the HTTP exchange
 * <li>{@link Logger logger} - the OpenIG logger for this filter
 * <li>{@link Handler next} - if the heap object is a filter then this variable
 * will contain the next handler in the filter chain.
 * </ul>
 */
public abstract class AbstractGroovyHeapObject extends GenericHeapObject {

    /** Creates and initializes a capture filter in a heap environment. */
    protected static abstract class AbstractGroovyHeaplet extends NestedHeaplet {
        @Override
        public abstract Object create() throws HeapException, JsonValueException;

        /**
         * Parses the configuration as a compiled Groovy script.
         *
         * @param script
         *            The name of the configuration value to parse as the
         *            script.
         * @return The compiled Groovy script.
         */
        protected final CompiledScript compile(final String script) {
            final String scriptFile = script + "File";
            if (config.isDefined(script)) {
                if (config.isDefined(scriptFile)) {
                    throw new JsonException("Both " + script + " and " + scriptFile
                            + " were specified, when at most one is allowed");
                }
                try {
                    return compiler.compile(config.get(script).asString());
                } catch (final ScriptException e) {
                    throw new JsonException("Unable to compile the Groovy script defined in '"
                            + script + "'", e);
                }
            } else if (config.isDefined(scriptFile)) {
                final File f = config.get(scriptFile).asFile();
                FileReader reader = null;
                try {
                    reader = new FileReader(f);
                    return compiler.compile(reader);
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
    // TODO: json/xml/http/sql/crest/ldap bindings.

    private static final String EOL = System.getProperty("line.separator");
    private static final ScriptEngineManager factory = new ScriptEngineManager();
    private static final ScriptEngine engine = factory.getEngineByName("groovy");
    private static final Compilable compiler = (Compilable) engine;
    private final CompiledScript compiledScript;

    /**
     * Creates a new Groovy heap object using the provided Groovy compiled
     * script.
     *
     * @param compiledScript
     *            The compiled Groovy script.
     */
    protected AbstractGroovyHeapObject(final CompiledScript compiledScript) {
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
        this(compiler.compile(joinAsString(EOL, (Object[]) scriptLines)));
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
        if (next != null) {
            bindings.put("next", next);
        }
        scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        // Redirect streams? E.g. in = request entity, out = response entity?
        return scriptContext;
    }
}
