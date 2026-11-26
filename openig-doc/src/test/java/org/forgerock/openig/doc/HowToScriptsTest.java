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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Executes the Groovy examples displayed by the How-To guides. */
@SuppressWarnings("javadoc")
public class HowToScriptsTest {
    private static final String ARTICLE = "src/main/asciidoc/how-to/"
            + "how-to-protect-web-services-with-openig.adoc";

    private String article() throws Exception {
        return String.join("\n", Files.readAllLines(Paths.get(ARTICLE), StandardCharsets.UTF_8));
    }

    @DataProvider
    public Object[][] acceptHeaders() {
        return new Object[][] {
            { null, "application/json;charset=UTF-8", 200 },
            { new String[] { "*/*" }, "application/json;charset=UTF-8", 200 },
            { new String[] { "application/*" }, "application/json", 200 },
            { new String[] { "application/json" }, "application/json;charset=UTF-8", 200 },
            { new String[] { "APPLICATION/JSON" }, "application/json", 200 },
            { new String[] { "application/json; charset=UTF-8" }, "application/json;charset=UTF-8", 200 },
            { new String[] { "application/json, text/plain" }, "application/json", 200 },
            { new String[] { "application/xml", "application/json" }, "application/json", 200 },
            { new String[] { "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" },
                    "application/json", 200 },
            { new String[] { "application/json;q=0.2" }, "application/json", 200 },
            { new String[] { "application/json;q=0, application/json" }, "application/json", 200 },
            { new String[] { "application/json, application/json;charset=utf-8;q=0" },
                    "application/json;charset=UTF-8", 406 },
            { new String[] { "application/json;q=0.5;level=1" }, "application/json", 406 },
            { new String[] { "application/json;q=.5" }, "application/json", 406 },
            { new String[] { "application/json;q=0, */*;q=1" }, "application/json", 406 },
            { new String[] { "application/*;q=0, */*;q=1" }, "application/json", 406 },
            { new String[] { "*/*;q=0, application/json;q=0.1" }, "application/json", 200 },
            { new String[] { "application/json; charset=utf-8;q=0, application/json;q=1" },
                    "application/json;charset=UTF-8", 406 },
            { new String[] { "application/json; charset=iso-8859-1" }, "application/json;charset=UTF-8", 406 },
            { new String[] { "application/json;profile=\"a,b\", application/xml" },
                    "application/json;profile=\"a,b\"", 200 },
            { new String[] { "application/json;profile=\"a,b\";q=0, application/json" },
                    "application/json;profile=\"a,b\"", 406 },
            { new String[] { "text/*" }, "application/json", 406 },
            { new String[] { "application/jsonp" }, "application/json", 406 },
            { new String[] { "application/xml" }, "application/json", 406 },
            { new String[] { "application/json;q=0" }, "application/json", 406 },
            { new String[] { "*/*;q=0" }, "application/json", 406 },
            { new String[] { "" }, "application/json", 406 },
            { new String[] { "application/json;q=invalid" }, "application/json", 406 },
            { new String[] { "application/json;q=1.5" }, "application/json", 406 },
            { new String[] { "not-a-media-type, application/json" }, "application/json", 200 },
            { new String[] { "application/json;foo" }, "application/json", 406 },
            { new String[] { "application/json;foo, application/json" }, "application/json", 200 },
            { new String[] { "application/json=garbage" }, "application/json", 406 },
            { new String[] { "application/json=garbage, application/json" }, "application/json", 200 },
            { new String[] { "application/json;q=0;q=1" }, "application/json", 406 },
            { new String[] { "application/json;q=0;q=1, application/json" }, "application/json", 200 },
            { new String[] { "application/xml" }, null, 200 },
            { new String[] { "*/*" }, "application/octet-stream; charset=binary", 200 },
            { new String[] { "*/*" }, "text/html; charset=x-unknown", 200 },
            { new String[] { "*/*" }, "text", 200 },
            { new String[] { "*/*" }, "", 200 },
            { new String[] { "*/*" }, ";", 200 },
            { new String[] { "*/*" }, "application/json; foo", 200 },
        };
    }

    @Test(dataProvider = "acceptHeaders")
    public void acceptFilterHonorsMediaRanges(String[] accepts, String contentType, int status) throws Exception {
        Request request = new Request();
        if (accepts != null) {
            request.getHeaders().put("Accept", accepts);
        }
        Response response = new Response(Status.OK);
        if (contentType != null) {
            response.getHeaders().add("Content-Type", contentType);
        }
        Response filtered = filter(request, response);
        assertThat(filtered.getStatus().getCode()).isEqualTo(status);
        if (status == 406) {
            assertThat(filtered.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
            assertThat(new ObjectMapper().readValue(filtered.getEntity().getString(), java.util.Map.class))
                    .isEqualTo(java.util.Collections.singletonMap("error", "Not Acceptable"));
        } else {
            assertThat(filtered).isSameAs(response);
        }
    }

    private Response filter(Request request, Response response) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/asciidoc/attachments/how-to/accept.groovy")),
                StandardCharsets.UTF_8);
        groovy.lang.Binding binding = new groovy.lang.Binding();
        binding.setVariable("request", request);
        binding.setVariable("context", new RootContext());
        binding.setVariable("next", (Handler) (context, incoming) -> Promises.newResultPromise(response));
        Script script = new GroovyShell(getClass().getClassLoader(), binding).parse(source);
        return (Response) ((Promise<?, ?>) script.run()).getOrThrow(5, TimeUnit.SECONDS);
    }

    private String listing(String text, String marker) {
        int start = text.indexOf(marker + "\n----\n");
        assertThat(start).as("Script listing %s", marker).isGreaterThanOrEqualTo(0);
        start += (marker + "\n----\n").length();
        return text.substring(start, text.indexOf("\n----", start));
    }

    private Script jwtScript(Binding binding) throws Exception {
        String text = article();
        return script(listing(text.substring(text.indexOf("`jwt.groovy`:\n")), "[source,Groovy]"), binding);
    }

    private Script script(String source, Binding binding) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ImportCustomizer()
                .addImports("org.forgerock.http.protocol.Response", "org.forgerock.http.protocol.Status"));
        return new GroovyShell(getClass().getClassLoader(), binding, configuration).parse(source);
    }

    private Response response(Object result) throws Exception {
        return result instanceof Promise
                ? (Response) ((Promise<?, ?>) result).getOrThrow(5, TimeUnit.SECONDS) : (Response) result;
    }

    @DataProvider
    public Object[][] errors() {
        return new Object[][] {
            { Status.UNAUTHORIZED, "Not Authenticated" },
            { Status.FORBIDDEN, "Forbidden" },
            { Status.UNAUTHORIZED, "bad 'quote' and \"double\"\nline\\slash" },
        };
    }

    @Test(dataProvider = "errors")
    public void jwtErrorsAreValidJson(Status status, String message) throws Exception {
        Script script = jwtScript(new Binding());
        Response response = (Response) script.invokeMethod("getErrorResponse", new Object[] { status, message });
        assertJsonError(response, status, message);
    }

    private void assertJsonError(Response response, Status status, String message) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(new ObjectMapper().readValue(response.getEntity().getString(), java.util.Map.class))
                .isEqualTo(java.util.Collections.singletonMap("error", message));
    }

    private Response runJwtScript(Request request, Map<String, String> issuers, List<String> allowedRoles,
                                  Map<String, Object> attributes, Handler next) throws Exception {
        Binding binding = new Binding();
        binding.setVariable("request", request);
        binding.setVariable("context", new RootContext());
        binding.setVariable("next", next);
        binding.setVariable("iss", issuers);
        binding.setVariable("audience", "sample-api");
        binding.setVariable("allowedRoles", allowedRoles);
        binding.setVariable("attributes", attributes);
        return response(jwtScript(binding).run());
    }

    private static String signedJwt(KeyPair keys, String claims) throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String signingInput = base64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8)) + "." + base64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64.encodeToString(signature.sign());
    }

    @DataProvider
    public Object[][] jwtClaims() {
        long now = System.currentTimeMillis() / 1000;
        return new Object[][] {
            { "sample-api", now + 600, "manager", 200 },
            { "sample-api", now + 600, "supervisor", 200 },
            { "sample-api", now + 600, "bad", 403 },
            { "sample-api", now - 600, "manager", 401 },
            { "sample-api", null, "manager", 401 },
            { "other-api", now + 600, "manager", 401 },
        };
    }

    @Test(dataProvider = "jwtClaims")
    public void jwtScriptChecksClaims(String audience, Long expiration, String role, int status) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Request request = new Request();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "test");
        claims.put("sub", "user-123");
        claims.put("name", "alice");
        claims.put("role", role);
        claims.put("aud", audience);
        if (expiration != null) {
            claims.put("exp", expiration);
        }
        String token = signedJwt(keys, new ObjectMapper().writeValueAsString(claims));
        request.getHeaders().put("Authorization", "Bearer " + token);
        Map<String, Object> attributes = new HashMap<>();
        AtomicBoolean forwarded = new AtomicBoolean();
        Handler next = (context, incoming) -> {
            forwarded.set(true);
            assertThat(incoming.getHeaders().getFirst("X-Auth-Username")).isEqualTo("alice");
            assertThat(attributes).containsEntry("sub", "user-123").containsEntry("role", role);
            return Promises.newResultPromise(new Response(Status.OK));
        };
        Response response = runJwtScript(request, Collections.singletonMap("test", pem),
                Arrays.asList("manager", "supervisor"), attributes, next);
        assertThat(response.getStatus().getCode()).isEqualTo(status);
        assertThat(forwarded.get()).isEqualTo(status == 200);
    }

    @Test
    public void jwtScriptRejectsMissingToken() throws Exception {
        Handler next = (context, incoming) -> {
            throw new AssertionError("next must not be called");
        };
        Response response = runJwtScript(new Request(), Collections.<String, String>emptyMap(),
                Collections.<String>emptyList(), new HashMap<>(), next);
        assertJsonError(response, Status.UNAUTHORIZED, "Not Authenticated");
    }

    @DataProvider
    public Object[][] logoutResponses() {
        return new Object[][] {
            { "blocking", "{\"result\":\"logged out\"}" },
            { "blocking", "invalid JSON" },
            { "async", "{\"result\":\"logged out\"}" },
            { "async", "invalid JSON" },
        };
    }

    @Test(dataProvider = "logoutResponses")
    public void logoutScriptClosesResponseBeforeContinuing(String mode, String body) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get("src/main/asciidoc/how-to/openig-http-client.adoc")),
                StandardCharsets.UTF_8);
        Request request = new Request();
        request.getHeaders().put("Cookie", "iPlanetDirectoryPro=demo-cookie");
        Response logoutResponse = new Response(Status.OK).setEntity(body);
        Binding binding = new Binding();
        binding.setVariable("request", request);
        binding.setVariable("openamUrl", "https://openam.example.org/openam");
        binding.setVariable("context", new RootContext());
        binding.setVariable("logger", LoggerFactory.getLogger(HowToScriptsTest.class));
        binding.setVariable("http", new Client((context, outgoing) -> {
            assertThat(outgoing.getUri().asURI().toString())
                    .isEqualTo("https://openam.example.org/openam/json/sessions/?_action=logout");
            assertThat(outgoing.getMethod()).isEqualTo("POST");
            assertThat(outgoing.getHeaders().getFirst("iPlanetDirectoryPro")).isEqualTo("demo-cookie");
            return Promises.newResultPromise(logoutResponse);
        }));
        Response nextResponse = new Response(Status.NO_CONTENT);
        binding.setVariable("next", (Handler) (context, incoming) -> {
            assertThat(incoming).isSameAs(request);
            try {
                assertThat(logoutResponse.getEntity().getString()).isEmpty();
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            return Promises.newResultPromise(nextResponse);
        });
        String source = listing(text, "[source#http-client-" + mode + "-script,groovy]");
        assertThat(response(script(source, binding).run())).isSameAs(nextResponse);
    }

    private Script guardScript(String file, Binding binding) throws Exception {
        return script(new String(Files.readAllBytes(Paths.get("src/main/asciidoc/attachments/how-to/ollama", file)),
                StandardCharsets.UTF_8), binding);
    }

    @Test(dataProvider = "errors")
    public void requestGuardErrorsAreValidJson(Status status, String message) throws Exception {
        Script script = guardScript("RequestGuardRail.groovy", new Binding());
        assertJsonError((Response) script.invokeMethod("generateErrorResponse", new Object[] { status, message }),
                status, message);
    }

    @Test(dataProvider = "errors")
    public void llmGuardErrorsAreValidJson(Status status, String message) throws Exception {
        Script script = guardScript("LlmGuardRail.groovy", new Binding());
        assertJsonError((Response) script.invokeMethod("generateErrorResponse", new Object[] { status, message }),
                status, message);
    }

    @DataProvider
    public Object[][] guardDecisions() {
        return new Object[][] {
            { "{\"error\":{\"message\":\"model not found\"}}", 403 },
            { "{\"choices\":[]}", 403 },
            { "{}", 403 },
            { "invalid JSON", 403 },
            { "{\"choices\":[{}]}", 403 },
            { "{\"choices\":[{\"message\":{\"content\":\"SAFE\"}}]}", 200 },
            { "{\"choices\":[{\"message\":{\"content\":\"INJECTION\"}}]}", 403 },
            { "{\"choices\":[{\"message\":{\"content\":\"SAFE\"}},"
                    + "{\"message\":{\"content\":\"INJECTION\"}}]}", 403 },
        };
    }

    @Test(dataProvider = "guardDecisions")
    public void llmGuardRequiresExplicitSafeDecision(String body, int status) throws Exception {
        Request request = new Request().setEntity("{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}");
        Response guardResponse = new Response(Status.OK).setEntity(body);
        AtomicBoolean forwarded = new AtomicBoolean();
        Binding binding = new Binding();
        binding.setVariable("request", request);
        binding.setVariable("context", new RootContext());
        binding.setVariable("model", "test");
        binding.setVariable("modelUri", "http://localhost/guard");
        binding.setVariable("logger", LoggerFactory.getLogger(HowToScriptsTest.class));
        binding.setVariable("http", new Client((context, incoming) -> Promises.newResultPromise(guardResponse)));
        binding.setVariable("next", (Handler) (context, incoming) -> {
            forwarded.set(true);
            return Promises.newResultPromise(new Response(Status.OK));
        });
        Response response = response(guardScript("LlmGuardRail.groovy", binding).run());
        assertThat(response.getStatus().getCode()).isEqualTo(status);
        assertThat(forwarded.get()).isEqualTo(status == 200);
        if (status == 403) {
            assertJsonError(response, Status.FORBIDDEN, "request is not allowed, prompt injection detected");
        }
    }
}
