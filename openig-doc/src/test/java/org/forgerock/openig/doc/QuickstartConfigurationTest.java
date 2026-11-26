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
package org.forgerock.openig.doc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.http.RunMode.PRODUCTION;
import static org.forgerock.openig.util.JsonValues.readJson;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.http.GatewayHttpApplication;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

/** Runs the configuration printed in the gateway Quickstart. */
@SuppressWarnings("javadoc")
public class QuickstartConfigurationTest {
    private static final Path ARTICLE = Path.of("src/main/asciidoc/gateway-guide/chap-quickstart.adoc");

    @Test
    public void quickstartRouteProxiesThePathAndQuery() throws Exception {
        String article = Files.readString(ARTICLE);
        String config = listing(article, "quickstart-config");
        String route = listing(article, "quickstart-route");
        ObjectMapper json = new ObjectMapper();
        assertThat(json.readTree(config).isObject()).isTrue();
        assertThat(json.readTree(route).isObject()).isTrue();
        assertThat(route).contains("http://127.0.0.1:8081");

        Path base = Files.createTempDirectory("openig-quickstart");
        try {
            HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.createContext("/", exchange -> {
                byte[] pathAndQuery = exchange.getRequestURI().toString().getBytes(UTF_8);
                try {
                    exchange.sendResponseHeaders(200, pathAndQuery.length);
                    exchange.getResponseBody().write(pathAndQuery);
                } finally {
                    exchange.close();
                }
            });
            upstream.start();
            try {
                Path configFile = Files.createDirectories(base.resolve("config")).resolve("config.json");
                Path routes = Files.createDirectory(base.resolve("config/routes"));
                Files.writeString(configFile, config);
                Files.writeString(routes.resolve("99-default.json"), route.replace(
                        "http://127.0.0.1:8081", "http://127.0.0.1:" + upstream.getAddress().getPort()));

                EndpointRegistry endpoints = new EndpointRegistry(new Router(), "/openig/api/system/objects");
                GatewayHttpApplication gateway = new GatewayHttpApplication(new DefaultEnvironment(base.toFile()),
                        readJson(configFile.toUri().toURL()), endpoints, PRODUCTION);
                try {
                    Handler handler = gateway.start();
                    Request request = new Request().setMethod("GET").setUri("http://localhost:8080/home?a=1");
                    try (Response response = handler.handle(new RootContext(), request).getOrThrow(5, SECONDS)) {
                        assertThat(response.getStatus()).isEqualTo(Status.OK);
                        assertThat(response.getEntity().getString()).isEqualTo("/home?a=1");
                    }
                } finally {
                    gateway.stop();
                }
            } finally {
                upstream.stop(0);
            }
        } finally {
            FileUtils.deleteDirectory(base.toFile());
        }
    }

    private static String listing(String article, String id) {
        Matcher matcher = Pattern.compile("(?ms)^\\[source#" + id + ",json]\\R----\\R(.*?)^----$").matcher(article);
        assertThat(matcher.find()).as("JSON listing with ID %s", id).isTrue();
        return matcher.group(1);
    }
}
