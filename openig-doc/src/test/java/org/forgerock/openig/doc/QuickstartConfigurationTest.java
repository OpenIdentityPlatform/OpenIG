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
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.http.GatewayHttpApplication;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Runs the configuration printed in the gateway Quickstart. */
@SuppressWarnings("javadoc")
public class QuickstartConfigurationTest {
    private static final Path ARTICLE = Path.of("src/main/asciidoc/gateway-guide/chap-quickstart.adoc");
    private static final Path TEMPLATES = ARTICLE.resolveSibling("chap-gateway-templates.adoc");
    private static final String SAMPLE_UPSTREAM = "http://app.example.com:8081";

    @Test
    public void quickstartRouteProxiesThePathAndQuery() throws Exception {
        String article = Files.readString(ARTICLE);
        String config = listing(article, "[source#quickstart-config,json]");
        String route = listing(article, "[source#quickstart-route,json]");
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

    @DataProvider
    public Object[][] passwordReplayTemplates() {
        return new Object[][] {
            { "simple", "example-simple-login", SAMPLE_UPSTREAM },
            { "cookie", "example-login-cookie", SAMPLE_UPSTREAM },
            { "classic", "example-login-replay-cookie-filters", SAMPLE_UPSTREAM },
            { "hidden", "example-login-hidden-value", SAMPLE_UPSTREAM },
            { "headers", "example-am-integration-headers", SAMPLE_UPSTREAM },
            { "headers", "example-owa-online", "https://login.microsoftonline.com" },
        };
    }

    @Test(dataProvider = "passwordReplayTemplates")
    public void passwordReplayTemplateProxiesMatchingRequests(String demo, String id, String upstream)
            throws Exception {
        String quickstart = Files.readString(ARTICLE);
        String templates = Files.readString(TEMPLATES);
        int example = templates.indexOf("[#" + id + "]");
        assertThat(example).as("Template %s", id).isNotNegative();
        String template = listing(templates.substring(example), "[source, javascript]")
                .replace("\"DESKEY\"", "\"QUJDREVGR0g=\"");

        Path base = Files.createTempDirectory("openig-templates");
        try {
            Path configFile = Files.createDirectories(base.resolve("config")).resolve("config.json");
            Path routes = Files.createDirectory(base.resolve("config/routes"));
            Files.writeString(configFile, withEchoClient(listing(quickstart, "[source#quickstart-config,json]")));
            Files.writeString(routes.resolve("99-default.json"), listing(quickstart, "[source#quickstart-route,json]"));
            Files.writeString(routes.resolve("20-template.json"), withEchoClient(template));

            EndpointRegistry endpoints = new EndpointRegistry(new Router(), "/openig/api/system/objects");
            GatewayHttpApplication gateway = new GatewayHttpApplication(new DefaultEnvironment(base.toFile()),
                    readJson(configFile.toUri().toURL()), endpoints, PRODUCTION);
            try {
                Handler handler = gateway.start();
                assertThat(upstreamRequest(handler, "/home?demo=" + demo))
                        .isEqualTo("GET " + upstream + "/home?demo=" + demo);
                if ("simple".equals(demo)) {
                    assertThat(upstreamRequest(handler, "/login?demo=simple"))
                            .isEqualTo("POST https://app.example.com:8444/login");
                }
            } finally {
                gateway.stop();
            }
        } finally {
            FileUtils.deleteDirectory(base.toFile());
        }
    }

    private static String upstreamRequest(Handler gateway, String path) throws Exception {
        Request request = new Request().setMethod("GET").setUri("http://openig.example.com:8080" + path);
        AttributesContext context = new AttributesContext(new SessionContext(new RootContext(), new InMemorySession()));
        try (Response response = gateway.handle(context, request).getOrThrow(5, SECONDS)) {
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            return response.getEntity().getString();
        }
    }

    private static String withEchoClient(String config) {
        return config.replace("\"type\": \"ClientHandler\"", "\"type\": \"" + EchoClient.class.getName() + "\"");
    }

    private static class InMemorySession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) {
        }
    }

    public static class EchoClient extends GenericHeaplet {
        @Override
        public Handler create() {
            return (context, request) -> newResultPromise(new Response(Status.OK)
                    .setEntity(request.getMethod() + " " + request.getUri().asURI()));
        }
    }

    private static String listing(String article, String marker) {
        Matcher matcher = Pattern.compile("(?ms)^" + Pattern.quote(marker) + "\\R----\\R(.*?)^----$").matcher(article);
        assertThat(matcher.find()).as("JSON listing %s", marker).isTrue();
        return matcher.group(1);
    }
}
