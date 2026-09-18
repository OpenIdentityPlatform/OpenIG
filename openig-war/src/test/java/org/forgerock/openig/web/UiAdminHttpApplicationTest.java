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
 * Portions Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openig.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.http.RunMode.EVALUATION;
import static org.forgerock.openig.web.OpenIGInitializerTest.getRelative;
import static org.forgerock.services.context.ClientContext.newInternalClientContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.forgerock.http.Handler;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class UiAdminHttpApplicationTest {

    @Test
    public void shouldServeTheUi() throws Exception {
        Environment env = new DefaultEnvironment(getRelative(getClass(), "doesnt-exist"));
        UiAdminHttpApplication module = new UiAdminHttpApplication("openig", json(object()), env, EVALUATION);
        Handler handler = module.start();

        Response response = handler.handle(newInternalClientContext(newUriRouterContext(new RootContext())),
                                           new Request().setMethod("GET").setUri("/studio/")).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getHeaders().getFirst(ContentTypeHeader.class)).isEqualTo("text/html");
    }

    @Test
    public void shouldUnpackRegularJarEntryIntoUnpackDirectory() throws Exception {
        File root = Files.createTempDirectory("openig-ui").toFile();
        File unpack = new File(root, "openig-ui");

        UiAdminHttpApplication.unpackJar(jarWithEntry("index.html", "<html/>"), unpack);

        assertThat(new File(unpack, "index.html")).hasContent("<html/>");
    }

    @Test
    public void shouldRejectJarEntryEscapingUnpackDirectory() throws Exception {
        File root = Files.createTempDirectory("openig-ui").toFile();
        File unpack = new File(root, "openig-ui");

        // A regular entry first, so that the unpack directory exists when the escaping entry is processed
        ByteArrayInputStream jar = jarWithEntries("index.html", "<html/>",
                                                  "../evil.txt", "boom");

        assertThatThrownBy(() -> UiAdminHttpApplication.unpackJar(jar, unpack))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("../evil.txt");
        assertThat(new File(root, "evil.txt")).doesNotExist();
    }

    private static ByteArrayInputStream jarWithEntry(String name, String content) throws IOException {
        return jarWithEntries(name, content);
    }

    /** Builds an in-memory jar from {@code name, content} pairs, in the given order. */
    private static ByteArrayInputStream jarWithEntries(String... nameContentPairs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                jar.putNextEntry(new JarEntry(nameContentPairs[i]));
                jar.write(nameContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static UriRouterContext newUriRouterContext(Context parent) {
        return new UriRouterContext(parent,
                                    "",
                                    "",
                                    Collections.<String, String>emptyMap());
    }
}
