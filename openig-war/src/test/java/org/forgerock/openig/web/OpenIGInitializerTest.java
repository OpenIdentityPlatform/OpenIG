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

package org.forgerock.openig.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenIGInitializerTest {

    @Test
    public void shouldUseDefaultConfigWhenConfigJsonIsMissing() throws Exception {
        Environment env = new DefaultEnvironment(getRelative(getClass(), "doesnt-exist"));
        OpenIGInitializer initializer = new OpenIGInitializer(env);

        URL url = initializer.selectConfigurationUrl("config.json");
        assertThat(url).isEqualTo(OpenIGInitializer.class.getResource("default-config.json"));
    }

    // Copied from Files (in openig-core/src/test/java)
    static File getRelative(final Class<?> base, final String name) {
        final URL url = base.getResource(base.getSimpleName() + ".class");
        File f;
        try {
            f = new File(url.toURI());
        } catch (URISyntaxException e) {
            f = new File(url.getPath());
        }
        return new File(f.getParentFile(), name);
    }
}
