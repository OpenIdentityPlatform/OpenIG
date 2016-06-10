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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.el.Bindings.bindings;

import java.io.File;
import java.net.URL;

import javax.script.ScriptException;

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AbstractScriptableHeapObjectTest {

    @Test
    public void shouldReturnAPromiseWrappingTheResult() throws Exception {
        AbstractScriptableHeapObject<Integer> scriptableObject = newScriptableObject("42");
        Promise<Integer, ScriptException> promise = scriptableObject.runScript(bindings(),
                                                                               new RootContext(),
                                                                               Integer.class);
        assertThat(promise.get()).isEqualTo(42);
    }

    @Test
    public void shouldReturnAPromiseWithNull() throws Exception {
        AbstractScriptableHeapObject<Integer> scriptableObject = newScriptableObject("null");
        Promise<Integer, ScriptException> promise = scriptableObject.runScript(bindings(),
                                                                               new RootContext(),
                                                                               Integer.class);
        assertThat(promise.get()).isNull();
    }

    @Test(expectedExceptions = ScriptException.class)
    public void shouldReturnAPromiseWithAnExceptionIfNotExpectedType() throws Exception {
        AbstractScriptableHeapObject<Integer> scriptableObject = newScriptableObject("'foo'");
        scriptableObject.runScript(bindings(), new RootContext(), Integer.class).getOrThrow();
    }

    private <T> AbstractScriptableHeapObject<T> newScriptableObject(final String... sourceLines)
            throws Exception {
        final Environment environment = getEnvironment();
        final Script script = Script.fromSource(environment, Script.GROOVY_MIME_TYPE, sourceLines);
        final Heap heap = new HeapImpl(Name.of("heap"));
        return new AbstractScriptableHeapObject<>(script, heap);
    }

    private Environment getEnvironment() throws Exception {
        return new DefaultEnvironment(new File(getTestBaseDirectory()));
    }

    /**
     * Implements a strategy to find the directory where groovy scripts are loadable.
     */
    private String getTestBaseDirectory() throws Exception {
        // relative path to our-self
        String name = resource(getClass());
        // find the complete URL pointing to our path
        URL resource = getClass().getClassLoader().getResource(name);

        // Strip out the 'file' scheme
        String path = new File(resource.toURI()).getPath();

        // Strip out the resource path to actually get the base directory
        return path.substring(0, path.length() - name.length());
    }

    private static String resource(final Class<?> type) {
        return type.getName().replace('.', '/').concat(".class");
    }


}
