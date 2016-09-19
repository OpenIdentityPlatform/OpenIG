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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.MutableUri.uri;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StaticRequestFilterTest {
    public static final String URI = "http://openig.forgerock.org";
    private TerminalHandler terminalHandler;

    private AttributesContext attributesContext;
    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        terminalHandler = new TerminalHandler();
        attributesContext = new AttributesContext(new RootContext());
        context = attributesContext;
    }

    @DataProvider
    private static Object[][] entityContentAndExpected() {
        return new Object[][] {
            { "${decodeBase64('RG9uJ3QgcGFuaWMu')}", "Don't panic." },
            /* See OPENIG-65 */
            { "{\"auth\":{\"passwordCredentials\":{"
                    + "\"username\":\"${attributes.username}\""
                    + ",\"password\":\"${attributes.password}\"}}}",
              "{\"auth\":{\"passwordCredentials\":{"
                    + "\"username\":\"bjensen\",\"password\":\"password\"}}}" },
            {"OpenIG", "OpenIG"}
        };
    }

    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("method", "GET"),
                    field("uri", URI),
                    field("entity", "a message"),
                    field("version", "2"))) },
            { json(object(
                    field("method", "GET"),
                    field("uri", URI),
                    field("entity", "${decodeBase64('YW4gZXhwcmVzc2lvbg==')}"),
                    field("version", "2"))) },
            { json(object(
                    field("method", "POST"),
                    field("uri", URI),
                    field("headers", object(field("Warning", array("418 I'm a teapot")))))) },
            { json(object(
                    field("method", "POST"),
                    field("uri", URI),
                    field("form", object(field("log", array("george")))))) } };
    }

    @DataProvider
    private Object[][] invalidConfigurations() {
        return new Object[][] {
            { json(object(
                    /* Missing method (required) */
                    field("uri", URI),
                    field("entity", "a message"),
                    field("version", "2"))) },
            { json(object(
                    /* Missing URI (required) */
                    field("method", "GET"),
                    field("entity", "${decodeBase64('RG9uJ3QgcGFuaWMu')}"),
                    field("version", "2"))) },
            { json(object(
                    /* Invalid entity type */
                    field("method", "GET"),
                    field("uri", URI),
                    field("entity", true),
                    field("version", "2"))) },
            { json(object(
                    /* Cannot have both entity && POST form set in heaplet */
                    field("method", "POST"),
                    field("uri", URI),
                    field("entity", json(object(field("field", "a message")))),
                    field("form", object(field("log", array("george")))),
                    field("version", "2"))) }};
    }

    @Test(dataProvider = "invalidConfigurations",
          expectedExceptions = { JsonValueException.class, HeapException.class })
    public void shouldFailToCreateHeaplet(final JsonValue config) throws Exception {
        final StaticRequestFilter.Heaplet heaplet = new StaticRequestFilter.Heaplet();
        heaplet.create(Name.of("myStaticRequestFilter"), config, buildDefaultHeap());
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final StaticRequestFilter.Heaplet heaplet = new StaticRequestFilter.Heaplet();
        final StaticRequestFilter filter = (StaticRequestFilter) heaplet.create(Name.of("myStaticRequestFilter"),
                                                                                config,
                                                                                buildDefaultHeap());
        filter.filter(context, null, terminalHandler);
        assertThat(terminalHandler.request).isNotNull();
        assertThat(terminalHandler.request.getUri()).isEqualTo(uri(URI));
    }

    /**
     * Minimal configuration test.
     * The handler should propagate the uri, method and version.
     */
    @Test
    public void testUriMethodAndVersionPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.setVersion("1.1");

        Request request = new Request();
        filter.filter(context, request, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(request);
        assertThat(terminalHandler.request).isNotNull();
        assertThat(terminalHandler.request.getUri()).isEqualTo(uri(URI));
        assertThat(terminalHandler.request.getMethod()).isEqualTo("GET");
        assertThat(terminalHandler.request.getVersion()).isEqualTo("1.1");
    }

    @Test
    public void testHeadersPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addHeaderValue("Mono-Valued", Expression.valueOf("First Value", String.class));
        filter.addHeaderValue("Multi-Valued", Expression.valueOf("One (1)", String.class));
        filter.addHeaderValue("Multi-Valued", Expression.valueOf("Two (${request.version})", String.class));

        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");

        filter.filter(context, original, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(original);
        // Check that headers have been properly populated
        assertThat(terminalHandler.request.getHeaders().get("Mono-Valued").getValues())
                .hasSize(1)
                .containsOnly("First Value");
        assertThat(terminalHandler.request.getHeaders().get("Multi-Valued").getValues())
                .hasSize(2)
                .containsOnly("One (1)", "Two (2)");
    }

    @Test
    public void testFormAttributesPropagationWithGetMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));
        filter.addFormParameter("multi", Expression.valueOf("one1", String.class));
        filter.addFormParameter("multi", Expression.valueOf("two${request.version}", String.class));

        // Needed to verify expression evaluation
        Request request = new Request();
        request.setVersion("2");

        filter.filter(context, request, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(request);
        // Verify that the new request URI contains the form's fields
        assertThat(terminalHandler.request.getUri().toString())
                .startsWith(URI)
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
        assertThat(terminalHandler.request.getEntity().getString()).isEmpty();
    }

    @Test
    public void testFormAttributesPropagationWithPostMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));
        filter.addFormParameter("multi", Expression.valueOf("one1", String.class));
        filter.addFormParameter("multi", Expression.valueOf("two${request.version}", String.class));

        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");

        filter.filter(context, original, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(original);
        // Verify that the new request entity contains the form's fields
        assertThat(terminalHandler.request.getMethod()).isEqualTo("POST");
        assertThat(terminalHandler.request.getHeaders().getFirst("Content-Type")).isEqualTo(
                "application/x-www-form-urlencoded");
        assertThat(terminalHandler.request.getEntity().getString())
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
    }

    @Test(dataProvider = "entityContentAndExpected")
    public void shouldAddRequestEntity(final String value, final String result) throws Exception {
        attributesContext.getAttributes().putAll(singletonMap("username", "bjensen"));
        attributesContext.getAttributes().putAll(singletonMap("password", "password"));

        final StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.setEntity(Expression.valueOf(value, String.class));

        Request request = new Request();
        filter.filter(context, request, terminalHandler);

        assertThat(terminalHandler.request).isNotSameAs(request).isNotNull();
        assertThat(terminalHandler.request.getUri()).isEqualTo(uri(URI));
        assertThat(terminalHandler.request.getMethod()).isEqualTo("POST");
        assertThat(terminalHandler.request.getEntity().getString()).isEqualTo(result);
    }

    @Test
    public void shouldPostFormOverrideRequestEntity() throws Exception {
        final StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.setEntity(Expression.valueOf("${decodeBase64('RG9uJ3QgcGFuaWMu')}", String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));

        Request request = new Request();
        filter.filter(context, request, terminalHandler);

        assertThat(terminalHandler.request).isNotSameAs(request).isNotNull();
        // The form combined with the POST method uses the Form#toRequestEntity
        // which overwrites any entity that may already be in the request.
        assertThat(terminalHandler.request.getEntity().getString()).isEqualTo("mono=one");
    }

    @Test
    public void shouldGetFormDoNotOverrideRequestEntity() throws Exception {
        final StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.setEntity(Expression.valueOf("${decodeBase64('RG9uJ3QgcGFuaWMu')}", String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));

        Request request = new Request();
        filter.filter(context, request, terminalHandler);

        assertThat(terminalHandler.request).isNotSameAs(request).isNotNull();
        assertThat(terminalHandler.request.getUri().toString()).startsWith(URI)
                                                               .contains("mono=one");
        assertThat(terminalHandler.request.getEntity().getString()).isEqualTo("Don't panic.");
    }

    private static class TerminalHandler implements Handler {
        Request request;
        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            this.request = request;
            return Promises.newResultPromise(new Response(Status.OK));
        }
    }
}
