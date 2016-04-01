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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.CookieHeader;
import org.forgerock.http.protocol.Cookie;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PasswordReplayFilterHeapletTest {

    static final String IS_GET_LOGIN_PAGE = "${matches(request.uri.path, '/login') and (request.method == 'GET')}";
    static final String HTTP_WWW_EXAMPLE_COM_LOGIN = "http://www.example.com/login";
    static final String HTTP_WWW_EXAMPLE_COM_HOME = "http://www.example.com/home";
    static final String HTTP_WWW_EXAMPLE_COM_PROTECTED = "http://www.example.com/protected";
    static final String FINAL_CONTENT_MARKER = "[this is the originally required content]";

    private HeapImpl heap;
    private Request protectedExampleGetRequest;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        heap = buildDefaultHeap();
        protectedExampleGetRequest = new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_PROTECTED);
        protectedExampleGetRequest.getHeaders().put("Host", "www.example.com");
        protectedExampleGetRequest.setVersion("HTTP/1.1+");
        protectedExampleGetRequest.getHeaders().put(new CookieHeader(singletonList(new Cookie().setName("a")
                                                                                               .setValue("b"))));
    }

    private Handler verifyAuthRequestOnlyIsSend() {
        return new Handler() {
            int index = 0;
            @Override
            public Promise<Response, NeverThrowsException> handle(final Context context,
                                                                  final Request request) {
                if (index++ == 0) {
                    assertThat(request.getMethod()).isEqualTo("POST");
                    assertThat(request.getForm().getFirst("username")).isEqualTo("demo");
                    assertThat(request.getForm().getFirst("password")).isEqualTo("changeit");
                    return newResponsePromise(new Response(Status.OK));
                }
                fail("Unexpected interaction");
                return newResponsePromise(newInternalServerError());
            }
        };
    }

    @Test
    public void shouldAuthenticateWhenQueryingLoginPage() throws Exception {
        Filter filter = builder().loginPage(IS_GET_LOGIN_PAGE)
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        attributesContext.getAttributes().put("username", "demo");
        attributesContext.getAttributes().put("password", "changeit");
        filter.filter(
                context,
                new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_LOGIN),
                verifyAuthRequestOnlyIsSend());
    }

    @Test
    public void shouldCallCredentialsAndDecryptionFiltersAndThenAuthenticateWhenQueryingLoginPage() throws Exception {
        Filter filter = builder().loginPage(IS_GET_LOGIN_PAGE)
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${request.headers['X-Username'][0]}")
                                                  .param("password", "${request.headers['X-Password'][0]}")
                                                  .build()
                                           .build()
                                 .credentials(new Filter() {
                                     @Override
                                     public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                                           final Request request,
                                                                                           final Handler next) {
                                         request.getHeaders().put("X-Username", "demo");
                                         request.getHeaders().put("X-Password", "sn/Wr2datgfvpSYSS8N1jA==");
                                         return next.handle(context, request);
                                     }
                                 })
                                 .headerDecryption(singletonList("X-Password"),
                                                   "DES/ECB/NoPadding",
                                                   "SoMI5WFkI0o=",
                                                   "DES")
                                 .build();

        filter.filter(newContextChain(),
                      new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_LOGIN),
                      verifyAuthRequestOnlyIsSend());
    }

    @Test
    public void shouldForwardUnchangedWhenNotQueryingLoginPage() throws Exception {
        Filter filter = builder().loginPage(IS_GET_LOGIN_PAGE)
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .build()
                                           .build()
                                 .build();

        final Request incoming = new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_HOME);
        filter.filter(
                newContextChain(),
                incoming,
                new Handler() {
                    @Override
                    public Promise<Response, NeverThrowsException> handle(final Context context,
                                                                          final Request request) {
                        assertThat(incoming).isSameAs(request);
                        return newResponsePromise(new Response(Status.OK));
                    }
                });
    }

    @Test
    public void shouldAuthenticateAfterLoginPageRequestHasReturned() throws Exception {
        Filter filter = builder().loginPage(IS_GET_LOGIN_PAGE)
                                 .extract("nonce", "nonce='(.*)'")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .param("nonce", "${attributes.extracted.nonce}")
                                           .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        attributesContext.getAttributes().put("username", "demo");
        attributesContext.getAttributes().put("password", "changeit");
        final Request incoming = new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_LOGIN);
        filter.filter(context,
                      incoming,
                      verifyAuthSequenceWithNonceReturned(incoming, "ae32f"));
    }

    @Test
    public void shouldIgnoreReturnedLoginPageIfInitialQueryIsNotTargetingLoginPage() throws Exception {
        Filter filter = builder().loginPage(IS_GET_LOGIN_PAGE)
                                 .extract("nonce", "nonce='(.*)'")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .param("nonce", "${attributes.extracted.nonce}")
                                                  .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        final Request incoming = new Request().setMethod("GET").setUri(HTTP_WWW_EXAMPLE_COM_HOME);
        filter.filter(context,
                      incoming,
                      verifyOriginalRequestIsForwarded(incoming,
                                                       new Response(Status.OK)
                                                               .setEntity("I'm a login page (nonce='ae32f')")));
    }

    @Test
    public void shouldAuthenticateWhenLoginPageIsReturned() throws Exception {
        Filter filter = builder().loginPageContentMarker("I'm a login page")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        attributesContext.getAttributes().put("username", "demo");
        attributesContext.getAttributes().put("password", "changeit");
        Response response = filter.filter(context,
                                          protectedExampleGetRequest,
                                          verifyAuthSequenceAfterReturnedLoginPage(protectedExampleGetRequest)).get();
        assertThat(response.getEntity().getString()).isEqualTo(FINAL_CONTENT_MARKER);
    }

    @Test
    public void shouldGetCredentialsFromFilterAndAuthenticateWhenLoginPageIsReturned() throws Exception {
        Filter filter = builder().loginPageContentMarker("I'm a login page")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${request.headers['X-Username'][0]}")
                                                  .param("password", "${request.headers['X-Password'][0]}")
                                                  .build()
                                           .build()
                                 .credentials(new Filter() {
                                     @Override
                                     public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                                           final Request request,
                                                                                           final Handler next) {
                                         request.getHeaders().put("X-Username", "demo");
                                         request.getHeaders().put("X-Password", "changeit");
                                         return next.handle(context, request);
                                     }
                                 })
                                 .build();

        Context context = newContextChain();
        Response response = filter.filter(context,
                                          protectedExampleGetRequest,
                                          verifyAuthSequenceAfterReturnedLoginPage(protectedExampleGetRequest)).get();
        assertThat(response.getEntity().getString()).isEqualTo(FINAL_CONTENT_MARKER);
    }

    @Test
    public void shouldGetEncryptedCredentialsFromFilterDecryptThemAndAuthenticateWhenLoginPageIsReturned()
            throws Exception {
        Filter filter = builder().loginPageContentMarker("I'm a login page")
                                 .request().uri("http://internal.example.com/login")
                                 .method("POST")
                                           .form().param("username", "${request.headers['X-Username'][0]}")
                                                  .param("password", "${request.headers['X-Password'][0]}")
                                                  .build()
                                           .build()
                                 .credentials(new Filter() {
                                     @Override
                                     public Promise<Response, NeverThrowsException> filter(final Context context,
                                                                                           final Request request,
                                                                                           final Handler next) {
                                         request.getHeaders().put("X-Username", "demo");
                                         request.getHeaders().put("X-Password", "sn/Wr2datgfvpSYSS8N1jA==");
                                         return next.handle(context, request);
                                     }
                                 })
                                 .headerDecryption(singletonList("X-Password"),
                                                 "DES/ECB/NoPadding",
                                                 "SoMI5WFkI0o=",
                                                 "DES")
                                 .build();

        Response response = filter.filter(newContextChain(),
                                          protectedExampleGetRequest,
                                          verifyAuthSequenceAfterReturnedLoginPage(protectedExampleGetRequest)).get();
        assertThat(response.getEntity().getString()).isEqualTo(FINAL_CONTENT_MARKER);
    }

    @Test
    public void shouldForwardWhenNoLoginPageIsReturned() throws Exception {
        Filter filter = builder().loginPageContentMarker("I'm a login page")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        Response expected = new Response(Status.OK).setEntity(FINAL_CONTENT_MARKER);
        Response response = filter.filter(context,
                                          protectedExampleGetRequest,
                                          verifyOriginalRequestIsForwarded(protectedExampleGetRequest,
                                                                           expected)).get();
        assertThat(response).isEqualTo(expected);
    }

    private Handler verifyOriginalRequestIsForwarded(final Request incoming, final Response response) {
        return new Handler() {
            int index = 0;
            @Override
            public Promise<Response, NeverThrowsException> handle(final Context context,
                                                                  final Request request) {
                switch (index++) {
                case 0:
                    // Should be the same content as the request forwarded
                    assertThatRequestsAreEquals(request, incoming);
                    return newResponsePromise(response);
                default:
                    fail("No other expected interactions");
                }
                return newResponsePromise(new Response(Status.INTERNAL_SERVER_ERROR));
            }
        };
    }

    @Test
    public void shouldAuthenticateWithExtractedValuesWhenLoginPageIsReturned() throws Exception {
        Filter filter = builder().loginPageContentMarker("I'm a login page")
                                 .extract("nonce", "nonce='(.*)'")
                                 .request().uri("http://internal.example.com/login")
                                           .method("POST")
                                           .form().param("username", "${attributes.username}")
                                                  .param("password", "${attributes.password}")
                                                  .param("nonce", "${attributes.extracted.nonce}")
                                                  .build()
                                           .build()
                                 .build();

        Context context = newContextChain();
        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        attributesContext.getAttributes().put("username", "demo");
        attributesContext.getAttributes().put("password", "changeit");
        Response response = filter.filter(context,
                                          protectedExampleGetRequest,
                                          verifyAuthSequenceWithNonceReturned(protectedExampleGetRequest, "ae32f"))
                                  .get();
        assertThat(response.getEntity().getString()).isEqualTo(FINAL_CONTENT_MARKER);
    }

    private Handler verifyAuthSequenceAfterReturnedLoginPage(final Request incoming) {
        return verifyAuthSequenceWithNonceReturned(incoming, null);
    }

    private Handler verifyAuthSequenceWithNonceReturned(final Request incoming, final String nonce) {
        return new Handler() {
            int index = 0;
            @Override
            public Promise<Response, NeverThrowsException> handle(final Context context,
                                                                  final Request request) {
                switch (index++) {
                case 0:
                    // Should be the same content as the request forwarded
                    assertThatRequestsAreEquals(request, incoming);
                    return newResponsePromise(new Response(Status.OK).setEntity(format("I'm a login page (nonce='%s')",
                                                                                       nonce)));
                case 1:
                    // Should be the authentication request
                    assertThat(request).isNotSameAs(incoming);
                    assertThat(request.getMethod()).isEqualTo("POST");
                    assertThat(request.getForm().getFirst("username")).isEqualTo("demo");
                    assertThat(request.getForm().getFirst("password")).isEqualTo("changeit");
                    if (nonce != null) {
                        assertThat(request.getForm().getFirst("nonce")).isEqualTo(nonce);
                    }
                    return newResponsePromise(new Response(Status.OK));
                case 2:
                    // The original request and incoming must be equals
                    assertThatRequestsAreEquals(request, incoming);
                    return newResponsePromise(new Response(Status.OK).setEntity(FINAL_CONTENT_MARKER));
                default:
                    fail("No other expected interactions");
                }
                return newResponsePromise(new Response(Status.INTERNAL_SERVER_ERROR));
            }
        };
    }

    private static void assertThatRequestsAreEquals(final Request original, final Request incoming) {
        assertThat(incoming.getMethod()).isEqualTo(original.getMethod());
        assertThat(incoming.getUri()).isEqualTo(original.getUri());
        try {
            assertThat(incoming.getEntity().getString()).isEqualTo(original.getEntity().getString());
        } catch (IOException e) {
            fail("An error occurred while reading the entity content");
        }
        assertThat(incoming.getHeaders().keySet()).isEqualTo(original.getHeaders().keySet());
        assertThat(incoming.getForm().toQueryString()).isEqualTo(original.getForm().toQueryString());
        assertThat(incoming.getVersion()).isEqualTo(original.getVersion());
    }

    private Context newContextChain() {
        return new SessionContext(new AttributesContext(new RootContext()), new InMemorySession());
    }

    private FilterBuilder builder() {
        return new FilterBuilder();
    }

    private class FilterBuilder {

        JsonValue config = json(object());
        private List<Map<String, Object>> extracts = new ArrayList<>();

        FilterBuilder loginPage(String loginPage) {
            config.put("loginPage", loginPage);
            return this;
        }

        FilterBuilder loginPageContentMarker(String loginPageContentMarker) {
            config.put("loginPageContentMarker", loginPageContentMarker);
            return this;
        }

        StaticRequestBuilder request() {
            return new StaticRequestBuilder(this);
        }

        FilterBuilder extract(String name, String pattern) {
            Map<String, Object> extract = new HashMap<>();
            extract.put("name", name);
            extract.put("pattern", pattern);
            extracts.add(extract);
            config.put("loginPageExtractions", extracts);
            return this;
        }

        FilterBuilder credentials(Filter credentials) {
            String uuid = UUID.randomUUID().toString();
            config.put("credentials", uuid);
            heap.put(uuid, credentials);
            return this;
        }

        FilterBuilder headerDecryption(List<String> names, String alg, String key, String keyType) {
            Map<String, Object> decrypt = new HashMap<>();
            decrypt.put("algorithm", alg);
            decrypt.put("key", key);
            decrypt.put("keyType", keyType);
            decrypt.put("headers", names);
            config.put("headerDecryption", decrypt);
            return this;
        }


        Filter build() throws Exception {
            return (Filter) new PasswordReplayFilterHeaplet().create(Name.of("this"), config, heap);
        }
    }

    private class StaticRequestBuilder {
        private FilterBuilder parent;
        JsonValue config = json(object());
        private Map<String, List<String>> headers = new HashMap<>();

        public StaticRequestBuilder(final FilterBuilder builder) {
            parent = builder;
        }

        StaticRequestBuilder method(String method) {
            config.put("method", method);
            return this;
        }

        StaticRequestBuilder uri(String uri) {
            config.put("uri", uri);
            return this;
        }

        StaticRequestBuilder header(String name, String value) {
            headers.put(name, singletonList(value));
            config.put("headers", headers);
            return this;
        }

        FormBuilder form() {
            return new FormBuilder(this);
        }

        FilterBuilder build() {
            parent.config.put("request", config.getObject());
            return parent;
        }
    }

    private class FormBuilder {
        private final StaticRequestBuilder builder;
        private Map<String, List<String>> params = new HashMap<>();

        public FormBuilder(final StaticRequestBuilder builder) {
            this.builder = builder;
        }

        FormBuilder param(String name, String value) {
            params.put(name, singletonList(value));
            builder.config.put("form", params);
            return this;
        }

        StaticRequestBuilder build() {
            return builder;
        }
    }

    private class InMemorySession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;
        @Override
        public void save(final Response response) throws IOException {
            // do nothing
        }
    }
}
