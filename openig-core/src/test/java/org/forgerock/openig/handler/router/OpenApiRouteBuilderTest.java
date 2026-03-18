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
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.forgerock.json.JsonValue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiRouteBuilderTest {

    private File tempDir;

    private OpenApiSpecLoader specLoader;
    private OpenApiRouteBuilder routeBuilder;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir      = Files.createTempDirectory("openig-route-builder-test").toFile();

        specLoader   = new OpenApiSpecLoader();
        routeBuilder = new OpenApiRouteBuilder();
    }

    @DataProvider(name = "pathToRegexCases")
    public static Object[][] pathToRegexCases() {
        return new Object[][] {
                { "/pets",                     "^/pets$"                        },
                { "/pets/{id}",                "^/pets/[^/]+$"                  },
                { "/pets/{petId}/photos",      "^/pets/[^/]+/photos$"           },
                { "/v1/{org}/{repo}/releases", "^/v1/[^/]+/[^/]+/releases$"    },
                { "/a.b/{x}",                  "^/a\\.b/[^/]+$"                 },
                { "/items/{id+}",              "^/items/[^/]+$"                 },
                { "/users",                    "^/users$"                       },
        };
    }

    @Test(dataProvider = "pathToRegexCases")
    public void pathToRegex_convertsTemplateToAnchoredRegex(
            final String input, final String expected) {
        assertThat(OpenApiRouteBuilder.pathToRegex(input)).isEqualTo(expected);
    }

    @Test
    public void pathToRegex_producedRegex_matchesConcreteUrls() {
        final String regex = OpenApiRouteBuilder.pathToRegex("/pets/{id}");
        assertThat("/pets/42".matches(regex)).isTrue();
        assertThat("/pets/fluffy-cat".matches(regex)).isTrue();
        // Must NOT match the collection endpoint or sub-paths
        assertThat("/pets".matches(regex)).isFalse();
        assertThat("/pets/42/photos".matches(regex)).isFalse();
    }

    @Test
    public void extractMethods_returnsOnlyDefinedMethods() {
        final PathItem item = new PathItem();
        item.setGet(new Operation());
        item.setPost(new Operation());

        final Set<String> methods = OpenApiRouteBuilder.extractMethods(item);
        assertThat(methods).containsExactly("GET", "POST");
    }


    @Test
    void buildRouteJson_usesSlugifiedInfoTitle_asRouteName() throws Exception {
        final File spec = writeYaml("petstore.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: 'Pet Store API'\n"
                        + "  version: '1.0.0'\n"
                        + "paths: {}\n");

        final JsonValue route = build(spec);
        assertThat(route.get("name").asString()).isEqualTo("pet-store-api");
    }

    @Test
    public void buildRouteJson_fallsBackToFileStem_whenTitleIsAbsent() throws IOException {
        final File spec = writeYaml("my-openapi-spec.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  version: '1.0.0'\n"
                        + "paths: {}\n");
        final JsonValue route = build(spec);
        assertThat(route.get("name").asString()).isEqualTo("my-openapi-spec");
    }

    @Test
    public void buildRouteJson_hasNoCondition_whenSpecHasNoPaths() throws IOException {
        final File spec = writeYaml("empty-paths.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Empty\n"
                        + "  version: '1'\n"
                        + "paths: {}\n");

        assertThat(build(spec).get("condition").isNull()).isTrue();
    }

    @Test
    public void buildRouteJson_conditionContainsExactPathRegex() throws IOException {
        final File spec = writeYaml("single.yaml", specWithPaths("/pets"));
        final String condition = build(spec).get("condition").asString();
        assertThat(condition).contains("^/pets$");
    }

    @Test
    public void buildRouteJson_conditionContainsMethodCheck() throws IOException {
        final File spec = writeYaml("single.yaml", specWithPaths("/pets"));
        final String condition = build(spec).get("condition").asString();
        assertThat(condition).containsIgnoringCase("request.method");
        assertThat(condition).contains("GET");
    }

    @Test
    public void buildRouteJson_paramInPath_isConvertedToNonSlashRegex() throws IOException {
        final File spec = writeYaml("param.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Test\n"
                        + "  version: '1'\n"
                        + "paths:\n"
                        + "  /pets/{petId}:\n"
                        + "    get:\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n");

        final String condition = build(spec).get("condition").asString();
        assertThat(condition).contains("[^/]+");
        assertThat(condition).doesNotContain("{petId}");
    }

    @Test
    public void buildRouteJson_multiplePathParamsAreAllConverted() throws IOException {
        final File spec = writeYaml("multi-param.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Test\n"
                        + "  version: '1'\n"
                        + "paths:\n"
                        + "  /orgs/{org}/repos/{repo}:\n"
                        + "    get:\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n");

        final String condition = build(spec).get("condition").asString();
        assertThat(countOccurrences(condition, "[^/]+")).isEqualTo(2);
    }

    @Test
    public void buildRouteJson_multipleMethodsForSamePath_generateSeparateClauses()
            throws IOException {
        final File spec = writeYaml("multi-method.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Test\n"
                        + "  version: '1'\n"
                        + "paths:\n"
                        + "  /pets:\n"
                        + "    get:\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n"
                        + "    post:\n"
                        + "      responses:\n"
                        + "        '201':\n"
                        + "          description: Created\n");

        final String condition = build(spec).get("condition").asString();
        assertThat(condition).contains("GET");
        assertThat(condition).contains("POST");

        assertThat(countOccurrences(condition, "^/pets$")).isEqualTo(2);
    }

    @Test
    public void buildRouteJson_multipleDistinctPaths_useOrOperator() throws IOException {
        final File spec = writeYaml("multi-path.yaml", specWithPaths("/users", "/orders"));
        final String condition = build(spec).get("condition").asString();
        assertThat(condition).contains("^/users$");
        assertThat(condition).contains("^/orders$");
        assertThat(condition).contains("||");
    }

    @Test
    public void buildRouteJson_heapContainsOpenApiValidationFilter() throws IOException {
        final File spec = writeYaml("petstore.yaml", specWithPaths("/pets"));
        final List<Object> heap = build(spec).get("heap").asList();
        assertThat(heap).isNotEmpty();

        final boolean hasValidator = heap.stream()
                .filter(o -> o instanceof java.util.Map)
                .map(o -> (java.util.Map<?, ?>) o)
                .anyMatch(m -> "OpenApiValidationFilter".equals(m.get("type")));
        assertThat(hasValidator).isTrue();
    }

    @Test
    public void buildRouteJson_validatorHeapObjectHasAbsoluteSpecFilePath() throws IOException {
        final File spec = writeYaml("petstore.yaml", specWithPaths("/pets"));
        final JsonValue route = build(spec);

        final java.util.Map<?, ?> validatorEntry = route.get("heap").asList().stream()
                .filter(o -> o instanceof java.util.Map)
                .map(o -> (java.util.Map<?, ?>) o)
                .filter(m -> "OpenApiValidationFilter".equals(m.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No OpenApiValidationFilter in heap"));

        final java.util.Map<?, ?> config = (java.util.Map<?, ?>) validatorEntry.get("config");
        assertThat(config.get("spec").toString()).contains(spec.getAbsolutePath());
    }

    @Test
    public void buildRouteJson_handlerIsChainWithClientHandler() throws IOException {
        final File spec = writeYaml("petstore.yaml", specWithPaths("/pets"));
        final JsonValue handler = build(spec).get("handler");
        assertThat(handler.get("type").asString()).isEqualTo("Chain");
        assertThat(handler.get("config").get("handler").asString()).isEqualTo("ClientHandler");
    }

    @Test
    public void buildRouteJson_chainFiltersContainValidatorName() throws IOException {
        final File spec = writeYaml("petstore.yaml", specWithPaths("/pets"));
        final List<String> filters = build(spec).get("handler")
                .get("config").get("filters").asList(String.class);
        assertThat(filters).isNotEmpty();
        assertThat(filters.stream().anyMatch(f -> f.toLowerCase().contains("validator")
                || f.toLowerCase().contains("openapi"))).isTrue();
    }

    // ---- baseURI ---------------------------------------------------------

    @Test
    public void buildRouteJson_setsBaseUri_whenSpecHasServer() throws IOException {
        final File spec = writeYaml("with-server.yaml",
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: API\n"
                        + "  version: '1'\n"
                        + "servers:\n"
                        + "  - url: 'https://api.example.com/v2'\n"
                        + "paths:\n"
                        + "  /items:\n"
                        + "    get:\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n");

        assertThat(build(spec).get("baseURI").asString()).isEqualTo("https://api.example.com/v2");
    }

    @Test
    public void buildRouteJson_hasNoBaseUri_whenSpecHasNoServer() throws IOException {
        final File spec = writeYaml("no-server.yaml", specWithPaths("/pets"));
        assertThat(build(spec).get("baseURI").isNull()).isTrue();
    }


    private File writeYaml(final String name, final String content) throws IOException {
        final File file = new File(tempDir, name);
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file;
    }

    private JsonValue build(final File specFile) {
        final Optional<OpenAPI> api = specLoader.tryLoad(specFile);
        assertThat(api.isPresent()).as("Expected spec file to parse successfully: " + specFile).isTrue();
        return routeBuilder.buildRouteJson(api.get(), specFile, true);
    }

    private static String specWithPaths(final String... paths) {
        final StringBuilder sb = new StringBuilder()
                .append("openapi: '3.0.3'\n")
                .append("info:\n")
                .append("  title: Test API\n")
                .append("  version: '1.0.0'\n")
                .append("paths:\n");
        for (final String path : paths) {
            sb.append("  ").append(path).append(":\n");
            sb.append("    get:\n");
            sb.append("      responses:\n");
            sb.append("        '200':\n");
            sb.append("          description: OK\n");
        }
        return sb.toString();
    }

    private static long countOccurrences(final String text, final String substring) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
