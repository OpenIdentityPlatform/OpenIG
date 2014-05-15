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

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import javax.script.ScriptException;

import org.forgerock.openig.config.Environment;

/**
 * A compiled script.
 */
public final class Script {
    /**
     * Groovy script implementation.
     */
    private final static class GroovyImpl implements Impl {
        private final GroovyScriptEngine engine;
        private final String fileName;

        private GroovyImpl(final GroovyScriptEngine engine, final String fileName)
                throws ScriptException {
            this.engine = engine;
            this.fileName = fileName;
        }

        @Override
        public Object run(final Map<String, Object> bindings) throws ScriptException {
            try {
                return engine.run(fileName, new Binding(bindings));
            } catch (final Exception e) {
                throw new ScriptException(e);
            }
        }
    }

    private interface Impl {
        Object run(Map<String, Object> bindings) throws ScriptException;
    }

    /**
     * The mime-type for Groovy scripts.
     */
    public static final String GROOVY_MIME_TYPE = "application/x-groovy";

    /**
     * The mime-type for Javascript scripts.
     */
    public static final String JS_MIME_TYPE = "text/javascript";

    private static final String EOL = System.getProperty("line.separator");

    private static final Object initializationLock = new Object();
    /** @GuardedBy initializationLock */
    private static volatile File groovyScriptCacheDir = null;
    /** @GuardedBy initializationLock */
    private static volatile GroovyScriptEngine groovyScriptEngine = null;

    /**
     * Loads a script having the provided content type and file name.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param file The location of the script to be loaded.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromFile(final Environment environment,
                                  final String mimeType,
                                  final String file) throws ScriptException {
        if (GROOVY_MIME_TYPE.equals(mimeType)) {
            final GroovyScriptEngine engine = getGroovyScriptEngine(environment);
            final Impl impl = new GroovyImpl(engine, file);
            return new Script(impl);
        } else {
            throw new ScriptException("Invalid script mime-type '" + mimeType + "': only '"
                    + GROOVY_MIME_TYPE + "' is supported");
        }
    }

    /**
     * Loads a script having the provided content type and content.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param sourceLines The script content.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromSource(final Environment environment,
                                    final String mimeType,
                                    final String... sourceLines) throws ScriptException {
        return fromSource(environment, mimeType, joinAsString(EOL, (Object[]) sourceLines));
    }

    /**
     * Loads a script having the provided content type and content.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param source The script content.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromSource(final Environment environment,
                                    final String mimeType,
                                    final String source) throws ScriptException {
        if (GROOVY_MIME_TYPE.equals(mimeType)) {
            final GroovyScriptEngine engine = getGroovyScriptEngine(environment);
            final File groovyScriptCacheDir = getGroovyScriptCacheDir();
            try {
                final File cachedScript =
                        File.createTempFile("script-", ".groovy", groovyScriptCacheDir);
                cachedScript.deleteOnExit();
                final FileWriter writer = new FileWriter(cachedScript);
                writer.write(source);
                writer.close();
                final Impl impl = new GroovyImpl(engine, cachedScript.getAbsolutePath());
                return new Script(impl);
            } catch (final IOException e) {
                throw new ScriptException(e);
            }
        } else {
            throw new ScriptException("Invalid script mime-type '" + mimeType + "': only '"
                    + GROOVY_MIME_TYPE + "' is supported");
        }
    }

    private static File getGroovyScriptCacheDir() throws ScriptException {
        File cacheDir = groovyScriptCacheDir;
        if (cacheDir != null) {
            return cacheDir;
        }

        synchronized (initializationLock) {
            cacheDir = groovyScriptCacheDir;
            if (cacheDir != null) {
                return cacheDir;
            }

            try {
                cacheDir = File.createTempFile("openig-groovy-script-cache-", null);
                cacheDir.delete();
                cacheDir.mkdir();
                cacheDir.deleteOnExit();
            } catch (final IOException e) {
                throw new ScriptException(e);
            }

            // Assign only after having fully initialized the cache directory.
            groovyScriptCacheDir = cacheDir;
            return cacheDir;
        }
    }

    private static GroovyScriptEngine getGroovyScriptEngine(final Environment environment)
            throws ScriptException {
        GroovyScriptEngine engine = groovyScriptEngine;
        if (engine != null) {
            return engine;
        }

        synchronized (initializationLock) {
            engine = groovyScriptEngine;
            if (engine != null) {
                return engine;
            }

            final String classPath = environment.getScriptsDir("groovy").getAbsolutePath();
            try {
                engine = new GroovyScriptEngine(classPath);
            } catch (final IOException e) {
                throw new ScriptException(e);
            }

            // Bootstrap the Groovy environment, e.g. add meta-classes.
            final URL bootstrap =
                    Script.class.getClassLoader().getResource("scripts/groovy/bootstrap.groovy");
            try {
                engine.run(bootstrap.toString(), new Binding());
            } catch (Exception e) {
                throw new ScriptException(e);
            }

            // Assign only after having fully initialized the engine.
            groovyScriptEngine = engine;
            return engine;
        }
    }

    private final Impl impl;

    private Script(final Impl impl) {
        this.impl = impl;
    }

    Object run(final Map<String, Object> bindings) throws ScriptException {
        return impl.run(bindings);
    }
}
