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

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Executes the Groovy examples displayed by the web-services How-To. */
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
            response.getHeaders().put("Content-Type", contentType);
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
        String source = listing("[source#how-to-protect-web-services-with-openig-accept-script,groovy]");
        groovy.lang.Binding binding = new groovy.lang.Binding();
        binding.setVariable("request", request);
        binding.setVariable("context", new RootContext());
        binding.setVariable("next", (Handler) (context, incoming) -> Promises.newResultPromise(response));
        Script script = new GroovyShell(getClass().getClassLoader(), binding).parse(source);
        return (Response) ((Promise<?, ?>) script.run()).getOrThrow();
    }

    private String listing(String marker) throws Exception {
        String text = article();
        int start = text.indexOf(marker + "\n----\n");
        assertThat(start).as("Script listing %s", marker).isGreaterThanOrEqualTo(0);
        start += (marker + "\n----\n").length();
        return text.substring(start, text.indexOf("\n----", start));
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
        String text = article();
        int start = text.indexOf("[source,Groovy]\n----\n", text.indexOf("`jwt.groovy`:\n"));
        assertThat(start).as("JWT script listing").isGreaterThanOrEqualTo(0);
        start = text.indexOf("\n----\n", start) + "\n----\n".length();
        String source = text.substring(start, text.indexOf("\n----", start));
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ImportCustomizer()
                .addImports("org.forgerock.http.protocol.Response"));
        Script script = new GroovyShell(getClass().getClassLoader(), new groovy.lang.Binding(), configuration)
                .parse(source);
        Response response = (Response) script.invokeMethod("getErrorResponse", new Object[] { status, message });
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(new ObjectMapper().readValue(response.getEntity().getString(), java.util.Map.class))
                .isEqualTo(java.util.Collections.singletonMap("error", message));
    }
}
