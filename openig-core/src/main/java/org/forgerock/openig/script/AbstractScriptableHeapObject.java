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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.openig.script;

import static java.lang.String.format;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.ldap.LdapClient;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scriptable heap object acts as a simple wrapper around the scripting engine.
 *
 * <p>This class is a base class for implementing any interface that we want to make pluggable
 * through scripting support.
 *
 * <p>Scripts are provided with the following variables bindings:
 * <ul>
 * <li>{@link Logger logger} - The logger for this script
 * <li>{@link Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link Context context} - the associated request context
 * <li>{@link Map contexts} - the visible contexts, keyed by context's name
 * <li>{@link Client http} - an HTTP client which may be used for performing outbound HTTP requests
 * <li>{@link LdapClient ldap} - an OpenIG LDAP client which may be used for
 * performing LDAP requests such as LDAP authentication
 * </ul>
 *
 * <p>The provided {@code args} parameters supports config-time expressions evaluation with the
 * special addition of a {@link Heap heap} variable that allows the script to get references
 * to other objects available in the heap.
 *
 * <pre>
 *     {@code {
 *         "args": {
 *             "ref": "heap['object-name']"
 *         }
 *     }}
 * </pre>
 *
 * <p>
 * <b>NOTE :</b> at the moment only Groovy is supported.
 *
 * @param <V> The expected result type of the {@link Promise}. As a convenience, this class supports non-Promise type to
 * be returned from the script, and will wrap it into a {@link Promise}.
 */
public class AbstractScriptableHeapObject<V> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractScriptableHeapObject.class);

    /** Creates and initializes a capture filter in a heap environment. */
    protected abstract static class AbstractScriptableHeaplet extends GenericHeaplet {
        private static final String CONFIG_OPTION_FILE = "file";
        private static final String CONFIG_OPTION_SOURCE = "source";
        private static final String CONFIG_OPTION_TYPE = "type";
        private static final String CONFIG_OPTION_ARGS = "args";

        @Override
        public Object create() throws HeapException {
            final Script script = compileScript();
            final AbstractScriptableHeapObject<?> component = newInstance(script, heap);
            Handler clientHandler = config.get("clientHandler")
                                          .defaultTo(CLIENT_HANDLER_HEAP_KEY)
                                          .as(requiredHeapObject(heap, Handler.class));
            component.setClientHandler(clientHandler);
            if (config.isDefined(CONFIG_OPTION_ARGS)) {
                Bindings bindings = heap.getProperties().bind("heap", heap);
                component.setArgs(config.get(CONFIG_OPTION_ARGS).as(evaluated(bindings)).asMap());
            }

            return component;
        }

        /**
         * Creates the new heap object instance using the provided script.
         *
         * @param script The compiled script.
         * @param heap The heap to look for bindings
         * @return The new heap object instance using the provided script.
         * @throws HeapException if an exception occurred during creation of the heap
         * object or any of its dependencies.
         * @throws JsonValueException if the heaplet (or one of its dependencies) has a
         * malformed configuration.
         */
        protected abstract AbstractScriptableHeapObject<?> newInstance(final Script script, final Heap heap)
                throws HeapException;

        private Script compileScript() throws HeapException {
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
                final String script = config.get(CONFIG_OPTION_FILE).as(evaluatedWithHeapProperties()).asString();
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
    private final Heap heap;
    private final String name;
    private Handler clientHandler;
    private final LdapClient ldapClient = LdapClient.getInstance();
    private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<>();
    private Map<String, Object> args;

    /**
     * Creates a new scriptable heap object using the provided compiled script.
     *
     * @param compiledScript
     *            The compiled script.
     * @param heap
     *            The heap to look for bindings
     * @param name
     *            The name of this scriptable heap object.
     */
    protected AbstractScriptableHeapObject(final Script compiledScript, final Heap heap, final String name) {
        this.compiledScript = compiledScript;
        this.heap = heap;
        this.name = name;
    }

    /**
     * Sets the HTTP client handler which should be made available to scripts.
     *
     * @param clientHandler The HTTP client handler which should be made available to scripts.
     */
    public void setClientHandler(final Handler clientHandler) {
        this.clientHandler = clientHandler;
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
     * Runs the compiled script using the provided bindings.
     *
     * @param bindings Base bindings available to the script (will be enriched).
     * @param context request processing context
     * @param clazz the class representing the expected result type of the {@code Promise}
     * @return the Promise of a Response produced by the script
     */
    @SuppressWarnings("unchecked")
    protected final Promise<V, ScriptException> runScript(final Bindings bindings,
                                                          final Context context,
                                                          final Class<V> clazz) {
        Object o;
        try {
            o = compiledScript.run(enrichBindings(bindings, context));
        } catch (ScriptException e) {
            logger.warn("Cannot execute script from '{}'", name, e);
            return newExceptionPromise(e);
        }
        if (o == null) {
            return newResultPromise(null);
        }
        if (o instanceof Promise) {
            return ((Promise<V, ScriptException>) o);
        }
        if (clazz.isInstance(o)) {
            return newResultPromise(clazz.cast(o));
        } else {
            return newExceptionPromise(new ScriptException(format("Expecting a result of type %s, got %s",
                                                                  clazz.getName(),
                                                                  o.getClass().getName())));
        }

    }

    private Map<String, Object> enrichBindings(final Bindings source, final Context context) throws ScriptException {
        // Set engine bindings.
        Bindings enriched = bindings().bind(heap.getProperties())
                                      .bind(source);
        final Map<String, Object> bindings = new HashMap<>();
        bindings.putAll(enriched.asMap());
        bindings.put("logger", getScriptLogger());
        bindings.put("globals", scriptGlobals);
        if (clientHandler != null) {
            bindings.put("http", new Client(clientHandler, context));
        }
        bindings.put("ldap", ldapClient);
        if (args != null) {
            for (final Entry<String, Object> entry : args.entrySet()) {
                final String key = entry.getKey();
                if (bindings.containsKey(key)) {
                    throw new ScriptException("Argument '" + key + "' can't override an existing binding");
                }
                bindings.put(entry.getKey(), entry.getValue());
            }
        }

        // Redirect streams? E.g. in = request entity, out = response entity?
        return bindings;
    }

    private Logger getScriptLogger() {
        return LoggerFactory.getLogger(format("%s.%s", this.getClass().getName(), name));
    }
}
