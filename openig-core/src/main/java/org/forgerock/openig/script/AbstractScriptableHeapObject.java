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
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.el.Expressions.evaluate;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.openig.util.JsonValues.evaluate;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.ldap.LdapClient;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * An abstract scriptable heap object which should be used as the base class for
 * implementing {@link org.forgerock.http.Filter filters} and {@link Handler handlers}. This heap
 * object acts as a simple wrapper around the scripting engine. Scripts are
 * provided with the following variable bindings:
 * <ul>
 * <li>{@link Map globals} - the Map of global variables which persist across
 * successive invocations of the script
 * <li>{@link org.forgerock.services.context.Context context} - the associated request context
 * <li>{@link Map contexts} - the visible contexts, keyed by context's name
 * <li>{@link Request request} - the HTTP request
 * <li>{@link Client http} - an HTTP client which may be used for performing outbound HTTP requests
 * <li>{@link LdapClient ldap} - an OpenIG LDAP client which may be used for
 * performing LDAP requests such as LDAP authentication
 * <li>{@link org.forgerock.openig.log.Logger logger} - the OpenIG logger
 * <li>{@link Handler next} - if the heap object is a filter then this variable
 * will contain the next handler in the filter chain.
 * <li>{@link Heap heap} - the heap.
 * </ul>
 * <p>
 * <b>NOTE:</b> at the moment only Groovy is supported.
 * <p><b>NOTE:</b> As of OpenIG 4.0, {@code exchange.request} and {@code exchange.response} are not set anymore.
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
            final AbstractScriptableHeapObject component = newInstance(script, heap);
            Handler clientHandler = heap.resolve(config.get("clientHandler").defaultTo(CLIENT_HANDLER_HEAP_KEY),
                                                 Handler.class);
            component.setClientHandler(clientHandler);
            if (config.isDefined(CONFIG_OPTION_ARGS)) {
                component.setArgs(config.get(CONFIG_OPTION_ARGS).asMap());
            }

            if (config.isDefined("httpClient")) {
                String message = format("'%s no longer uses a 'httpClient' attribute: 'clientHandler' "
                                                + "has to be used instead with a reference to a Handler",
                                        name);
                logger.warning(message);
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
        protected abstract AbstractScriptableHeapObject newInstance(final Script script, final Heap heap)
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
                final String script = evaluate(config.get(CONFIG_OPTION_FILE));
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
    private Handler clientHandler;
    private final LdapClient ldapClient = LdapClient.getInstance();
    private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<>();
    private Map<String, Object> args;

    /**
     * Creates a new scriptable heap object using the provided compiled script.
     *
     * @param compiledScript The compiled script.
     * @param heap The heap to look for bindings
     */
    protected AbstractScriptableHeapObject(final Script compiledScript, Heap heap) {
        this.compiledScript = compiledScript;
        this.heap = heap;
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
     * Runs the compiled script using the provided bindings and optional
     * forwarding handler.
     *
     * @param bindings Base bindings available to the script (will be enriched).
     * @param next The next handler in the chain if applicable, may be
     * {@code null}.
     * @param context request processing context
     * @return the Promise of a Response produced by the script
     */
    @SuppressWarnings("unchecked")
    protected final Promise<Response, NeverThrowsException> runScript(final Bindings bindings,
                                                                      final Handler next,
                                                                      final Context context) {
        try {
            Object o = compiledScript.run(enrichBindings(bindings, next, context));
            if (o instanceof Promise) {
                return ((Promise<Response, NeverThrowsException>) o);
            } else if (o instanceof Response) {
                // Allow to return a response directly from script
                return newResponsePromise((Response) o);
            } else {
                logger.error("Script did not return a Response or a Promise<Response, NeverThrowsException>");
                return newResponsePromise(newInternalServerError());
            }
        } catch (final ScriptException e) {
            logger.warning("Cannot execute script");
            logger.warning(e);
            return newResponsePromise(newInternalServerError().setCause(e));
        }
    }

    private Map<String, Object> enrichBindings(final Bindings source, final Handler next, final Context context)
            throws ScriptException {
        // Set engine bindings.
        final Map<String, Object> bindings = new HashMap<>();
        bindings.putAll(source.asMap());
        bindings.put("logger", logger);
        bindings.put("globals", scriptGlobals);
        if (clientHandler != null) {
            bindings.put("http", new Client(clientHandler, context));
        }
        bindings.put("ldap", ldapClient);
        if (next != null) {
            bindings.put("next", next);
        }
        if (args != null) {
            try {
                final Bindings exprEvalBindings = bindings().bind(source).bind("heap", heap);
                for (final Entry<String, Object> entry : args.entrySet()) {
                    checkBindingNotAlreadyUsed(bindings, entry.getKey());
                    bindings.put(entry.getKey(), evaluate(entry.getValue(), exprEvalBindings));
                }
            } catch (ExpressionException ex) {
                throw new ScriptException(ex);
            }
        }

        // Redirect streams? E.g. in = request entity, out = response entity?
        return bindings;
    }

    private void checkBindingNotAlreadyUsed(final Map<String, Object> bindings,
                                            final String key) throws ScriptException {
        if (bindings.containsKey(key)) {
            final String errorMsg = "Can't override the binding named " + key;
            logger.error(errorMsg);
            throw new ScriptException(errorMsg);
        }
    }
}
