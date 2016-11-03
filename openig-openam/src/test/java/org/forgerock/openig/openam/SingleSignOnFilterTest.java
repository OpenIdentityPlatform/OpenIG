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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.openam;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.json.resource.http.CrestHttp.newRequestHandler;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.util.Collections;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SingleSignOnFilterTest {

    private static final URI OPENAM_URI = URI.create("http://www.example.com:8090/openam/");
    private static final String COOKIE_NAME = "iPlanetDirectoryPro";
    private static final String RESOURCE_URI = "http://example.com/index.html";
    private static final String TOKEN = "ARrrg...42*";
    private static final String MY_REALM = "/myRealm";
    private UriRouterContext context;
    private Request request;

    @Captor
    private ArgumentCaptor<Context> captor;

    @Mock
    private Handler next;

    @Mock
    private RequestHandler requestHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        request = new Request();
        request.setMethod("GET").setUri(RESOURCE_URI);
        context = new UriRouterContext(new RootContext(),
                                       "",
                                       "",
                                       Collections.<String, String>emptyMap(),
                                       new URI(RESOURCE_URI));
    }

    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(field("openamUrl", OPENAM_URI.toASCIIString()))) },
            { json(object(field("openamUrl", OPENAM_URI.toASCIIString()),
                          field("cookieName", "myCookieName"))) },
            { json(object(field("openamUrl", OPENAM_URI.toASCIIString()),
                          field("cookieName", "myCookieName"),
                          field("amHandler", "amHandler"))) },
            { json(object(field("openamUrl", OPENAM_URI.toASCIIString()),
                          field("realm", MY_REALM))) }};
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final SingleSignOnFilter.Heaplet heaplet = new SingleSignOnFilter.Heaplet();
        final Filter filter = (SingleSignOnFilter) heaplet.create(Name.of("myFilter"),
                                                                  config,
                                                                  buildDefaultHeap());
        assertThat(filter).isNotNull();
    }

    @DataProvider
    private static Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing URL to OpenAM. */
            { json(object()) },
            /* Invalid URL to OpenAM. */
            { json(object(field("openamUrl", "http://[invalidUri"))) } };
    }

    @Test(dataProvider = "invalidConfigurations",
          expectedExceptions = { JsonValueException.class, HeapException.class })
    public void shouldFailToCreateHeaplet(final JsonValue invalidConfiguration) throws Exception {
        final SingleSignOnFilter.Heaplet heaplet = new SingleSignOnFilter.Heaplet();
        heaplet.create(Name.of("myFilter"), invalidConfiguration, buildDefaultHeap());
    }

    @DataProvider
    private static Object[][] invalidParameters() {
        return new Object[][]{
            { null, mock(Handler.class) },
            { OPENAM_URI, null }
        };
    }

    @Test(dataProvider = "invalidParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailToCreateSingleSignOnFilter(final URI openamUrl,
                                                     final Handler amHandler) {
        new SingleSignOnFilter(openamUrl, MY_REALM, COOKIE_NAME, newRequestHandler(amHandler, openamUrl));
    }

    @Test
    public void shouldRedirectToOpenAmWhenNoCookiePresent() throws Exception {
        // When
        final Response response = buildSingleSignOnFilter().filter(context, request, next).get();

        // Then
        verifyZeroInteractions(next, requestHandler);
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().getFirst(LocationHeader.NAME))
                .contains(OPENAM_URI.toASCIIString(), "?goto=" + context.getOriginalUri() + "&realm=" + MY_REALM);
    }

    @Test
    public void shouldSucceedToRetrieveSsoTokenAndContinue() {
        // Given
        final Filter filter = buildSingleSignOnFilter();
        appendRequestSsoTokenCookie(request, COOKIE_NAME);
        when(requestHandler.handleAction(eq(context), any(ActionRequest.class)))
                .thenReturn(validSsoToken());

        // When
        filter.filter(context, request, next);

        // Then
        verify(requestHandler).handleAction(eq(context), any(ActionRequest.class));
        verify(next).handle(captor.capture(), eq(request));
        final SsoTokenContext ssoTokenContext = captor.getValue()
                                                      .asContext(SsoTokenContext.class);
        assertThat(ssoTokenContext.getInfo()).containsOnly(entry("valid", true),
                                                           entry("uid", "demo"),
                                                           entry("realm", "/myRealm"));
        assertThat(ssoTokenContext.getValue()).isEqualTo(TOKEN);
    }

    @Test
    public void shouldSucceedToRetrieveSsoTokenAndRedirect() throws Exception {
        // Given
        final Filter filter = buildSingleSignOnFilter();
        appendRequestSsoTokenCookie(request, COOKIE_NAME);
        // The token is present but the validation is false. Session outdated.
        when(requestHandler.handleAction(eq(context), any(ActionRequest.class)))
                .thenReturn(invalidSsoToken());

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(requestHandler).handleAction(eq(context), any(ActionRequest.class));
        verifyZeroInteractions(next);
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().getFirst(LocationHeader.NAME))
                .contains(OPENAM_URI.toASCIIString(), "?goto=" + context.getOriginalUri() + "&realm=" + MY_REALM);
    }

    @Test
    public void shouldSucceedWhenMultipleCookieHeadersFoundAndContinue() {
        // Given
        final Filter filter = buildSingleSignOnFilter();
        // Add twice the same request cookie
        appendRequestSsoTokenCookie(request, COOKIE_NAME, TOKEN);
        appendRequestSsoTokenCookie(request, COOKIE_NAME, "invalidToken");

        when(requestHandler.handleAction(eq(context), any(ActionRequest.class)))
                .thenReturn(validSsoToken());

        // When
        filter.filter(context, request, next);

        // Then
        verify(requestHandler).handleAction(eq(context), any(ActionRequest.class));
        verify(next).handle(captor.capture(), eq(request));
        assertThat(captor.getValue().asContext(SsoTokenContext.class).getValue()).isEqualTo(TOKEN);
    }

    @DataProvider
    private static Object[][] sessionValidationFailsFromServer() {
        return new Object[][]{
            { json(null) },
            { json(object(field("code", 503),
                          field("reason", "Service Unavailable"),
                          field("message", "An error occurred"))) },
            { json(object(field("code", 410),
                          field("reason", "Gone"),
                          field("message", "Token not found"))) } };
    }

    @Test(dataProvider = "sessionValidationFailsFromServer")
    public void shouldFailWhenCallingForSessionValidationFails(final JsonValue content) throws Exception {
        // Given
        final Filter filter = buildSingleSignOnFilter();
        appendRequestSsoTokenCookie(request, COOKIE_NAME);

        when(requestHandler.handleAction(any(Context.class), any(ActionRequest.class)))
                .thenReturn(newActionResponse(content).asPromise());

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(requestHandler).handleAction(eq(context), any(ActionRequest.class));
        verifyZeroInteractions(next);
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
    }

    private SingleSignOnFilter buildSingleSignOnFilter() {
        return new SingleSignOnFilter(OPENAM_URI, COOKIE_NAME, MY_REALM, requestHandler);
    }

    private static void appendRequestSsoTokenCookie(final Request request, final String cookieName) {
        appendRequestSsoTokenCookie(request, cookieName, null);
    }

    private static void appendRequestSsoTokenCookie(final Request request,
                                                    final String cookieName,
                                                    final String cookieValue) {
        request.getHeaders().add("Cookie",
                                 singletonList(format("%s=%s;",
                                                      cookieName,
                                                      cookieValue != null ? cookieValue : TOKEN)));
    }

    private static Promise<ActionResponse, ResourceException> validSsoToken() {
        return newActionResponse(json(object(field("valid", true),
                                             field("uid", "demo"),
                                             field("realm", MY_REALM))))
                .asPromise();
    }

    private static Promise<ActionResponse, ResourceException> invalidSsoToken() {
        return newActionResponse(json(object(field("valid", false)))).asPromise();
    }

    private static HeapImpl buildDefaultHeap() {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(FORGEROCK_CLIENT_HANDLER_HEAP_KEY, mock(Handler.class));
        heap.put("amHandler", mock(Handler.class));
        return heap;
    }
}
