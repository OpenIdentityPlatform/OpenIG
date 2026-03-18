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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.forgerock.openig.handler.router.OpenApiRouteBuilder;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class IT_SwaggerRoute {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiRouteBuilder.class);

    private WireMockServer wireMockServer;
    @BeforeClass
    public void setupWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8090));
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        stubFor(get(urlPathEqualTo("/v2/pet/findByStatus"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\n" +
                                "{\"id\": 1, \"name\": \"Bella\", \"status\": \"available\"},\n" +
                                "{\"id\": 2, \"name\": \"Charlie\", \"status\": \"available\"}\n" +
                                "]")));
    }

    @AfterClass
    public void tearDownWireMock() {
        wireMockServer.stop();
    }

    @Test
    public void testSwaggerRoute() throws IOException {
        String testConfigPath = getTestConfigPath();
        Path destination = Path.of(testConfigPath, "config", "routes", "petstore.yaml");

        try(InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("routes/petstore.yaml")) {
            Objects.requireNonNull(inputStream, "routes/petstore.yaml resource missing");
            Files.createDirectories(destination.getParent());
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        testPetRoute("openapi-petstore", destination);
    }

    @Test
    public void testCustomRoute() throws IOException {
        String testConfigPath = getTestConfigPath();
        String openApiSpec = this.getClass().getClassLoader().getResource("routes/petstore.yaml").getPath();
        String routeContents = IOUtils.resourceToString("routes/01-find-pet.json", StandardCharsets.UTF_8, this.getClass().getClassLoader())
                .replace("$$SWAGGER_FILE$$", openApiSpec);
        Path destination = Path.of(testConfigPath, "config", "routes", "01-find-pet.json");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, routeContents);

        testPetRoute("01-find-pet", destination);
    }

    private void testPetRoute(String routeId, Path destination) throws IOException {
        try {
            await().pollInterval(3, SECONDS)
                    .atMost(15, SECONDS).until(() -> routeAvailable(routeId));

            RestAssured
                    .given().when().get("/v2/pet/findByStatus?status=available")
                    .then()
                    .statusCode(200)
                    .body("[0].id", Matchers.equalTo(1));
        } finally {
            Files.delete(destination);
        }

        await().pollInterval(3, SECONDS)
                .atMost(15, SECONDS).until(() -> !routeAvailable(routeId));

        RestAssured
                .given().when().get("/v2/pet/findByStatus?status=available")
                .then()
                .statusCode(404);
    }
    
    public boolean routeAvailable(String routeId) {
        Response response = RestAssured
                .given().when().
                get("/openig/api/system/objects/_router/routes?_queryFilter=true");

        String res = response.then().extract().path("result[0]._id");
        return Objects.equals(res, routeId);
    }

    private String getTestConfigPath() {
        return System.getProperty("test.config.path");
    }


}
