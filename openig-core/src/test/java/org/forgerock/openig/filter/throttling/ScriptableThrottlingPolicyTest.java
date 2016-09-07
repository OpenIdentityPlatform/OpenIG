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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openig.filter.throttling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.util.Collections;

import org.forgerock.http.filter.throttling.ThrottlingRate;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.script.Script;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ScriptableThrottlingPolicyTest {

    @Test
    public void shouldLookupThrottlingRate() throws Exception {
        ScriptableThrottlingPolicy dataSource = newGroovyThrottlingPolicy(
                "import org.forgerock.util.time.Duration",
                "import org.forgerock.http.filter.throttling.*",
                "new ThrottlingRate(numberOfRequests, Duration.duration('10 seconds'))"
        );
        dataSource.setArgs(Collections.<String, Object>singletonMap("numberOfRequests", 42));

        ThrottlingRate rate = dataSource.lookup(new RootContext(), new Request()).get();

        assertThat(rate.getNumberOfRequests()).isEqualTo(42);
    }

    private ScriptableThrottlingPolicy newGroovyThrottlingPolicy(final String... sourceLines) throws Exception {
        final Environment environment = getEnvironment();
        final Script script = Script.fromSource(environment, Script.GROOVY_MIME_TYPE, sourceLines);
        final Heap heap = new HeapImpl(Name.of("heap"));
        return new ScriptableThrottlingPolicy(script, heap, "myThrottlingPolicy");
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
        File f = new File(resource.toURI());
        String path = f.getPath();

        // Strip out the resource path to actually get the base directory
        return path.substring(0, path.length() - name.length());
    }

    private static String resource(final Class<?> type) {
        return type.getName().replace('.', '/').concat(".class");
    }

}
