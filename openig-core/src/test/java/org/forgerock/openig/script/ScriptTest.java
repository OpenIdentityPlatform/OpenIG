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
 * Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openig.script;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.script.Script.GROOVY_MIME_TYPE;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ScriptTest {

    private Environment environment;

    @BeforeMethod
    public void setUp() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("File permissions can only be checked on a POSIX file system");
        }
        environment = new DefaultEnvironment(Files.createTempDirectory("openig-script-test").toFile());
    }

    @Test
    public void shouldCreateInlineScriptCacheDirectoryReadableByOwnerOnly() throws Exception {
        Script.fromSource(environment, GROOVY_MIME_TYPE, "return 42");

        Path cacheDir = groovyScriptCacheDir().toPath();
        assertThat(Files.getPosixFilePermissions(cacheDir))
                .containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
    }

    @Test
    public void shouldCreateInlineScriptFileReadableByOwnerOnly() throws Exception {
        Script.fromSource(environment, GROOVY_MIME_TYPE, "return 42");

        Path cacheDir = groovyScriptCacheDir().toPath();
        try (var scripts = Files.list(cacheDir)) {
            assertThat(scripts).isNotEmpty().allSatisfy(script ->
                    assertThat(Files.getPosixFilePermissions(script))
                            .containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE));
        }
    }

    private static File groovyScriptCacheDir() throws Exception {
        Field field = Script.class.getDeclaredField("groovyScriptCacheDir");
        field.setAccessible(true);
        return (File) field.get(null);
    }
}
