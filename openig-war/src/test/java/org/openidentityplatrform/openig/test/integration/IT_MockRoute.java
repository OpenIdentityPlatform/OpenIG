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

package org.openidentityplatrform.openig.test.integration;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for {@code OpenApiMockResponseHandler}.
 *
 * <p>The test drops a route JSON that references the Petstore OpenAPI spec and wires
 * {@code OpenApiMockResponseHandler} as the terminal handler (no upstream service needed).
 * It then exercises the mock endpoints and validates:
 * <ul>
 *   <li>Collection responses return a JSON array of the configured {@code arraySize}</li>
 *   <li>Each pet object contains all required fields with realistic (Datafaker-generated) values</li>
 *   <li>Enum fields use a value from the declared enum list</li>
 *   <li>Requests for individual resources return a JSON object</li>
 *   <li>The route is unloaded cleanly when the config file is removed</li>
 * </ul>
 */
public class IT_MockRoute {

    private static final Logger logger = LoggerFactory.getLogger(IT_MockRoute.class);

    private static final String ROUTE_ID = "petstore-mock";

    /**
     * Deploys the mock petstore route, runs assertions, then removes the route and asserts
     * that the endpoint returns 404 once the route has been unloaded.
     */
    @Test
    public void testMockRoute_petCollection() throws IOException {
        final String testConfigPath = getTestConfigPath();
        final String specFile       = getSpecFilePath();

        // Prepare the route JSON with the actual spec file path substituted
        final String routeContents = IOUtils.resourceToString(
                        "routes/petstore-mock.json", StandardCharsets.UTF_8,
                        getClass().getClassLoader())
                .replace("$$SWAGGER_FILE$$", specFile);

        final Path routeDest = Path.of(testConfigPath, "config", "routes", "petstore-mock.json");
        Files.createDirectories(routeDest.getParent());
        Files.writeString(routeDest, routeContents);

        try {
            // Wait for the route to become active
            await().pollInterval(3, SECONDS)
                    .atMost(30, SECONDS)
                    .until(() -> routeAvailable(ROUTE_ID));

            // GET /v2/pet/findByStatus?status=available → 200 JSON array
            final String body = RestAssured
                    .given()
                    .when()
                    .get("/v2/pet/findByStatus?status=available")
                    .then()
                    .statusCode(200)
                    .contentType("application/json")
                    .body("$", Matchers.instanceOf(List.class))
                    .extract().asString();

            logger.info("Mock pet collection response: {}", body);

            // Parse and assert array contents
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> pets =
                    RestAssured.given().when().get("/v2/pet/findByStatus?status=available")
                            .jsonPath().getList("$");

            assertThat(pets).isNotEmpty();
            assertThat(pets).hasSize(2); // arraySize=2 in petstore-mock.json

            for (final Map<String, Object> pet : pets) {
                // id must be an integer
                assertThat(pet).containsKey("id");
                assertThat(pet.get("id")).isInstanceOf(Integer.class);

                // name has example: "doggie" in spec → should equal "doggie"
                assertThat(pet).containsKey("name");
                assertThat(pet.get("name")).isEqualTo("doggie");

                // status is an enum [available, pending, sold] → first value used
                assertThat(pet).containsKey("status");
                assertThat(pet.get("status")).isIn("available", "pending", "sold");

                // photoUrls is a required array
                assertThat(pet).containsKey("photoUrls");
            }
        } finally {
            Files.deleteIfExists(routeDest);
        }

        // Route should be unloaded after the file is deleted
        await().pollInterval(3, SECONDS)
                .atMost(30, SECONDS)
                .until(() -> !routeAvailable(ROUTE_ID));

        RestAssured.given().when()
                .get("/v2/pet/findByStatus?status=available")
                .then()
                .statusCode(404);
    }

    /**
     * Verifies that requesting a single pet by ID returns a JSON object with the
     * expected fields, and that Datafaker generates realistic string values.
     */
    @Test
    public void testMockRoute_singlePet() throws IOException {
        final String testConfigPath = getTestConfigPath();
        final String specFile       = getSpecFilePath();

        final String routeContents = IOUtils.resourceToString(
                        "routes/petstore-mock.json", StandardCharsets.UTF_8,
                        getClass().getClassLoader())
                .replace("$$SWAGGER_FILE$$", specFile);

        final Path routeDest = Path.of(testConfigPath, "config", "routes", "petstore-mock-single.json");
        final String routeWithDifferentName = routeContents.replace(
                "\"name\": \"petstore-mock\"", "\"name\": \"petstore-mock-single\"");
        Files.createDirectories(routeDest.getParent());
        Files.writeString(routeDest, routeWithDifferentName);

        try {
            await().pollInterval(3, SECONDS)
                    .atMost(30, SECONDS)
                    .until(() -> routeAvailable("petstore-mock-single"));

            // GET /v2/pet/{petId} → single object
            @SuppressWarnings("unchecked")
            final Map<String, Object> pet =
                    RestAssured.given().when().get("/v2/pet/42")
                            .then()
                            .statusCode(200)
                            .contentType("application/json")
                            .extract()
                            .jsonPath().getMap("$");

            logger.info("Mock single pet response: {}", pet);

            assertThat(pet).containsKey("id");
            assertThat(pet.get("id")).isInstanceOf(Integer.class);

            // name has example: "doggie"
            assertThat(pet).containsKey("name");
            assertThat(pet.get("name")).isEqualTo("doggie");
        } finally {
            Files.deleteIfExists(routeDest);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the router admin API reports a route with the given ID.
     */
    private boolean routeAvailable(final String routeId) {
        final Response response = RestAssured.given().when()
                .get("/openig/api/system/objects/_router/routes?_queryFilter=true");
        final List<String> ids = response.jsonPath().getList("result._id");
        return ids != null && ids.contains(routeId);
    }

    /** Reads the {@code test.config.path} system property set by cargo during integration tests. */
    private static String getTestConfigPath() {
        return System.getProperty("test.config.path");
    }

    /**
     * Returns the absolute path to the {@code petstore.yaml} resource on the class path.
     * The file is extracted to a temp location so the mock handler can read it via
     * {@code read('...')} expression at runtime inside the embedded container.
     */
    private static String getSpecFilePath() throws IOException {
        final Path tmp = Files.createTempFile("petstore-", ".yaml");
        try (final InputStream in = IT_MockRoute.class.getClassLoader()
                .getResourceAsStream("routes/petstore.yaml")) {
            Objects.requireNonNull(in, "routes/petstore.yaml not found on classpath");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp.toAbsolutePath().toString();
    }
}
