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

import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Common utilities for compiling scripts. Provides a simple abstraction from
 * JSR 223 / commons scripting.
 */
public final class Scripts {
    /**
     * The mime-type for Groovy scripts.
     */
    public static final String GROOVY_MIME_TYPE = "application/x-groovy";

    /**
     * The mime-type for Javascript scripts.
     */
    public static final String JS_MIME_TYPE = "text/javascript";

    private static final ScriptEngineManager factory = new ScriptEngineManager();

    static CompiledScript compileScript(final String mimeType, final String script)
            throws FileNotFoundException, ScriptException {
        final File f = new File(script);
        final FileReader reader = new FileReader(f);
        try {
            final ScriptEngine engine = Scripts.getScriptEngineByMimeType(mimeType);
            return ((Compilable) engine).compile(reader);
        } finally {
            closeSilently(reader);
        }
    }

    static CompiledScript compileSource(final String mimeType, final String source)
            throws ScriptException {
        final ScriptEngine engine = Scripts.getScriptEngineByMimeType(mimeType);
        return ((Compilable) engine).compile(source);
    }

    /**
     * Returns the scripting engine and bootstraps language specific bindings.
     */
    private static ScriptEngine getScriptEngineByMimeType(final String mimeType)
            throws ScriptException {
        if (!GROOVY_MIME_TYPE.equals(mimeType)) {
            throw new ScriptException("Invalid script mime-type '" + mimeType + "': only '"
                    + GROOVY_MIME_TYPE + "' is supported");
        }
        final ScriptEngine engine = factory.getEngineByMimeType(mimeType);

        /*
         * Make LDAP attributes properties of an LDAP entry so that they can be
         * accessed using the dot operator. The setter explicitly constructs an
         * Attribute in order to take advantage of the various overloaded
         * constructors. In particular, it allows scripts to assign multiple
         * values at once (see unit tests for examples).
         */
        engine.eval("org.forgerock.opendj.ldap.Entry.metaClass.getProperty ="
                + "{ key -> delegate.getAttribute(key) }");
        engine.eval("org.forgerock.opendj.ldap.Entry.metaClass.setProperty ="
                + "{ key, values -> delegate.replaceAttribute("
                + "                 new org.forgerock.opendj.ldap.LinkedAttribute(key, values)) }");

        return engine;
    }

    private Scripts() {
        // Prevent instantiation.
    }
}
