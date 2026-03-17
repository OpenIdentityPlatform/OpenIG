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
 * Copyright 2026 3A Systems LLC.
 */

package org.forgerock.openig.handler.router;

import io.swagger.v3.oas.models.OpenAPI;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiSpecLoaderTest {

    private File tempDir;

    private OpenApiSpecLoader loader;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("openig-spec-loader-test").toFile();
        loader  = new OpenApiSpecLoader();
    }

    @AfterMethod
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    @Test
    public void isOpenApiFile_returnsFalse_forNullFile() {
        assertThat(loader.isOpenApiFile(null)).isFalse();
    }

    @Test
    public void isOpenApiFile_returnsFalse_forRegularOpenIGRouteJson() throws IOException {
        final File f = write("route.json",
                "{ \"name\": \"my-route\", \"handler\": { \"type\": \"ClientHandler\" } }");
        assertThat(loader.isOpenApiFile(f)).isFalse();
    }

    @Test
    public void isOpenApiFile_returnsTrue_forOpenApi3JsonFile() throws IOException {
        final File f = write("petstore.json", minimalOpenApi3Json());
        assertThat(loader.isOpenApiFile(f)).isTrue();
    }

    @Test
    public void isOpenApiFile_returnsTrue_forOpenApi3YamlFile() throws IOException {
        final File f = write("petstore.yaml", minimalOpenApi3Yaml());
        assertThat(loader.isOpenApiFile(f)).isTrue();
    }

    @Test
    public void isOpenApiFile_returnsTrue_forYmlExtension() throws IOException {
        final File f = write("petstore.yml", minimalOpenApi3Yaml());
        assertThat(loader.isOpenApiFile(f)).isTrue();
    }

    @Test
    public void isOpenApiFile_returnsFalse_forUnsupportedExtension() throws IOException {
        final File f = write("spec.xml", "<root/>");
        assertThat(loader.isOpenApiFile(f)).isFalse();
    }

    @Test
    public void isOpenApiFile_returnsFalse_forEmptyFile() throws IOException {
        final File f = write("empty.json", "");
        assertThat(loader.isOpenApiFile(f)).isFalse();
    }

    @Test
    public void isOpenApiFile_returnsFalse_forMalformedJson() throws IOException {
        final File f = write("broken.json", "{ not valid json }}}");
        assertThat(loader.isOpenApiFile(f)).isFalse();
    }

    @Test
    public void isOpenApiFile_returnsTrue_forSwagger2JsonFile() throws IOException {
        final File f = write("swagger2.json",
                "{ \"swagger\": \"2.0\", \"info\": { \"title\": \"T\", \"version\": \"1\" },"
                        + " \"paths\": {}, \"basePath\": \"/\" }");
        assertThat(loader.isOpenApiFile(f)).isTrue();
    }

    // ---- tryLoad ---------------------------------------------------------

    @Test
    public void tryLoad_returnsEmpty_forNonOpenApiFile() throws IOException {
        final File f = write("route.json",
                "{ \"name\": \"r\", \"handler\": { \"type\": \"ClientHandler\" } }");
        assertThat(loader.tryLoad(f).isEmpty()).isTrue();
    }

    @Test
    public void tryLoad_returnsOpenAPI_forValidOpenApi3Json() throws IOException {
        final File f = write("petstore.json", minimalOpenApi3Json());
        final Optional<OpenAPI> result = loader.tryLoad(f);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getInfo().getTitle()).isEqualTo("Petstore");
    }

    @Test
    public void tryLoad_returnsOpenAPI_forValidOpenApi3Yaml() throws IOException {
        final File f = write("petstore.yaml", minimalOpenApi3Yaml());
        final Optional<OpenAPI> result = loader.tryLoad(f);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getInfo().getTitle()).isEqualTo("Petstore");
    }

    @Test
    public void tryLoad_populatesPaths() throws IOException {
        final File f = write("petstore.yaml", minimalOpenApi3Yaml());
        final Optional<OpenAPI> result = loader.tryLoad(f);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getPaths()).containsKey("/pets");
    }

    @Test
    public void tryLoad_doesNotThrow_forMalformedSpec() throws IOException {
        final File f = write("bad.yaml", "openapi: 3.0.0\ninfo: ~\nrandom garbage: !!!");
        assertThat(loader.tryLoad(f).isEmpty()).isTrue();
    }

    private File write(final String name, final String content) throws IOException {
        final File file = new File(tempDir, name);
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(final File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        final File[] children = dir.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        dir.delete();
    }

    private static String minimalOpenApi3Json() {
        return "{\n"
                + "  \"openapi\": \"3.0.3\",\n"
                + "  \"info\": { \"title\": \"Petstore\", \"version\": \"1.0.0\" },\n"
                + "  \"paths\": {\n"
                + "    \"/pets\": {\n"
                + "      \"get\": {\n"
                + "        \"summary\": \"List all pets\",\n"
                + "        \"operationId\": \"listPets\",\n"
                + "        \"responses\": {\n"
                + "          \"200\": { \"description\": \"OK\" }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
    }

    private static String minimalOpenApi3Yaml() {
        return "openapi: \"3.0.3\"\n"
                + "info:\n"
                + "  title: Petstore\n"
                + "  version: \"1.0.0\"\n"
                + "paths:\n"
                + "  /pets:\n"
                + "    get:\n"
                + "      summary: List all pets\n"
                + "      operationId: listPets\n"
                + "      responses:\n"
                + "        '200':\n"
                + "          description: OK\n";
    }
}