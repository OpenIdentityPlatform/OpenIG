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
package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.oauth2.OAuth2Error.E_INVALID_CLIENT;
import static org.forgerock.http.oauth2.OAuth2Error.E_INVALID_TOKEN;
import static org.forgerock.http.oauth2.OAuth2Error.E_TEMPORARILY_UNAVAILABLE;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.BAD_GATEWAY;
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.http.protocol.Status.FORBIDDEN;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.MOVED_PERMANENTLY;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.protocol.Status.TEAPOT;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.DEFAULT_SCOPE;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildAuthorizedOAuth2Session;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildClientRegistration;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildClientRegistrationWithScopes;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.newSession;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.el.LeftValueExpression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2ClientFilterTest {

    private static final String ACCESS_TOKEN_HAS_EXPIRED = "The access token has expired";
    private static final String DEFAULT_CLIENT_REGISTRATION_NAME = "openam";
    private static final String DEFAULT_CLIENT_ENDPOINT = "/openid";
    private static final String DEFAULT_OPENID_CLIENT_REGISTRATION_NAME = "openam-openid";
    private static final String NEW_ACCESS_TOKEN = "2YoLoFZFEjr1zCsicMWpAA*";
    private static final String NEW_REFRESH_TOKEN = "tGzv3JOkF0XG5Qx2TlKWAAA";
    private static final String ORIGINAL_URI = "http://www.example.com:443";
    private static final String REQUESTED_URI = "http://www.example.com/myapp/endpoint";
    private static final String TARGET = "openid";

    private AttributesContext attributesContext;
    private ClientRegistrationRepository registrations;
    private Context context;
    private Request request;
    private Response failureResponse;
    private SessionContext sessionContext;

    @Mock
    private Handler discoveryAndDynamicRegistrationChain;

    @Mock
    private Handler failureHandler, loginHandler, next, registrationHandler;

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        attributesContext = new AttributesContext(new RootContext());
        sessionContext = new SessionContext(attributesContext, newSession());
        registrations = new ClientRegistrationRepository();
        request = new Request();
        failureResponse = new Response(INTERNAL_SERVER_ERROR);
        failureResponse.setEntity("An error occured");
        when(failureHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(failureResponse));
    }

    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            // Minimal configuration with login handler.
            { json(object(
                    field("clientEndpoint", DEFAULT_CLIENT_ENDPOINT),
                    field("failureHandler", "myFailureHandler"),
                    field("loginHandler", "myLoginHandler"))) },
            // Minimal configuration with registration.
            { json(object(
                    field("clientEndpoint", DEFAULT_CLIENT_ENDPOINT),
                    field("failureHandler", "myFailureHandler"),
                    field("registrations", DEFAULT_CLIENT_REGISTRATION_NAME))) },
            // Minimal configuration with registration (array).
            { json(object(
                    field("clientEndpoint", DEFAULT_CLIENT_ENDPOINT),
                    field("failureHandler", "myFailureHandler"),
                    field("registrations", array(DEFAULT_CLIENT_REGISTRATION_NAME)))) } };
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final OAuth2ClientFilter.Heaplet heaplet = new OAuth2ClientFilter.Heaplet();
        assertThat(heaplet.create(Name.of("myCustomObject"), config, buildDefaultHeap())).isNotNull();
    }

    @DataProvider
    private static Object[][] missingRequiredAttributes() {
        return new Object[][] {
            // Missing clientEndpoint.
            { json(object(
                    field("failureHandler", "myFailureHandler"),
                    field("loginHandler", "myLoginHandler"))) },
            // Missing failure handler.
            { json(object(
                    field("clientEndpoint", DEFAULT_CLIENT_ENDPOINT),
                    field("loginHandler", "myLoginHandler"))) },
            // Missing login handler or registration.
            { json(object(
                    field("clientEndpoint", DEFAULT_CLIENT_ENDPOINT),
                    field("failureHandler", "myFailureHandler"))) } };
    }

    @Test(dataProvider = "missingRequiredAttributes",
          expectedExceptions = { JsonValueException.class, HeapException.class })
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        final OAuth2ClientFilter.Heaplet heaplet = new OAuth2ClientFilter.Heaplet();
        heaplet.create(Name.of("myCustomObject"), config, buildDefaultHeap());
    }

    /************************************************************************************************************/
    /** handleUserInitiatedDiscovery case
    /************************************************************************************************************/

    private void setUpForHandleUserInitiatedDiscoveryCases(final String discoveryUri) throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI + DEFAULT_CLIENT_ENDPOINT + discoveryUri));
        request.setUri(ORIGINAL_URI + discoveryUri);
    }

    @Test
    public void shouldSucceedToHandleUserInitiatedDiscovery() throws Exception {
        // Given
        setUpForHandleUserInitiatedDiscoveryCases("/login?discovery=bjensen@example.com&goto=redirectUri");
        when(discoveryAndDynamicRegistrationChain.handle(eq(context), eq(request)))
            .thenReturn(newResponsePromise(new Response(TEAPOT)));
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(discoveryAndDynamicRegistrationChain).handle(eq(context), eq(request));
        assertThat(response.getStatus()).isEqualTo(TEAPOT);
        verifyZeroInteractions(failureHandler, loginHandler, next, registrationHandler);
    }

    /************************************************************************************************************/
    /** handleUserInitiatedLogin case
    /************************************************************************************************************/

    private void setUpForHandleUserInitiatedLoginCases(final String loginUri) throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI + DEFAULT_CLIENT_ENDPOINT + loginUri));

        request.setUri(ORIGINAL_URI + loginUri);
        registrations.add(buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME, registrationHandler));
    }

    @DataProvider
    private static Object[][] loginUri() {
        return new Object[][] {
            { "/login?registration=unknown" },
            { "/login" } };
    }

    @Test(dataProvider = "loginUri")
    public void shouldHandleUserInitiatedLoginFailsWithInvalidClientRegistration(final String loginUri)
            throws Exception {
        // Given
        setUpForHandleUserInitiatedLoginCases(loginUri);
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, loginHandler, next, registrationHandler);
    }

    @Test
    public void shouldHandleUserInitiatedLoginFailsWhenRequestIsNotSufficientlySecure() throws Exception {
        // Given
        setUpForHandleUserInitiatedLoginCases("/login?registration=" + DEFAULT_CLIENT_REGISTRATION_NAME);
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(true);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, loginHandler, next, registrationHandler);
    }

    @Test
    public void shouldHandleUserInitiatedLoginSucceedToAuthorizationRedirect() throws Exception {
        // Given
        setUpForHandleUserInitiatedLoginCases("/login?registration=" + DEFAULT_CLIENT_REGISTRATION_NAME);
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThatAuthorizationRedirectHandlerProducesRedirect(response);
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, failureHandler, loginHandler, next,
                               registrationHandler);
    }

    /************************************************************************************************************/
    /** handleAuthorizationCallback case
    /************************************************************************************************************/

    private void setUpForHandleAuthorizationCallbackCases(final String uri) throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI + DEFAULT_CLIENT_ENDPOINT + uri));
        request.setMethod("GET");
        request.setUri(ORIGINAL_URI + uri);
    }

    @Test
    public void shouldFailToHandleAuthorizationCallbackWhenRequestIsNotSufficientlySecure() throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(true);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @DataProvider
    private static Object[][] requestMethod() {
        return new Object[][] {
            { "POST" },
            { "CONNECT" },
            { "PATCH" },
            { "PUT" },
            { "DELETE" },
            { "HEAD" } };
    }

    @Test(dataProvider = "requestMethod")
    public void shouldFailToHandleAuthorizationCallbackWhenRequestIsMethodNotGet(final String method) throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback");
        request.setMethod(method);
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldFailToHandleAuthorizationCallbackWhenSessionNotAuthorized() throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldFailToHandleAuthorizationCallbackWhenSessionStateIsNotAuthorizing() throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback?state=af0ifjsldkj");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldFailToHandleAuthorizationCallbackWhenNoCodeProvided() throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback?state=af0ifjsldkj");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldFailToHandleAuthorizationCallbackWhenNoClientRegistrationSpecified() throws Exception {
        // Given
        setUpForHandleAuthorizationCallbackCases("/callback?state=af0ifjsldkj&code=authorizationCode");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verifyZeroInteractions(next, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldSucceedToHandleAuthorizationCallback() throws Exception {
        // Given
        when(registrationHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(
                    buildOAuth2Response(OK, json(object(field("access_token", NEW_ACCESS_TOKEN),
                                                        field("refresh_token", NEW_REFRESH_TOKEN),
                                                        field("expires_in", 1000),
                                                        field("id_token", OAuth2TestUtils.ID_TOKEN))))));
        setUpForHandleAuthorizationCallbackCases("/callback?state=af0ifjsldkj:redirectUri&code=authorizationCode");
        registrations.add(buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME, registrationHandler));
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("redirectUri");
        verify(registrationHandler).handle(eq(context), any(Request.class));
        verifyZeroInteractions(next, failureHandler, discoveryAndDynamicRegistrationChain);
    }

    /************************************************************************************************************/
    /** handleUserInitiatedLogout case
    /************************************************************************************************************/

    private void setUpForHandleUserInitiatedLogoutCases(final String logoutUri) throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI + DEFAULT_CLIENT_ENDPOINT + logoutUri));
        request.setUri(ORIGINAL_URI + logoutUri);
    }

    @Test
    public void shouldSucceedToHandleUserInitiatedLogoutWithoutGoto() throws Exception {
        // Given
        setUpForHandleUserInitiatedLogoutCases("/logout");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThatSessionIsEmpty();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, failureHandler, loginHandler, next,
                               registrationHandler);
    }

    @Test
    public void shouldSucceedToHandleUserInitiatedLogoutWithGoto() throws Exception {
        // Given
        setUpForHandleUserInitiatedLogoutCases("/logout?goto=www.forgerock.com");
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter().setRequireHttps(false);

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("www.forgerock.com");
        assertThatSessionIsEmpty();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, failureHandler, loginHandler, next,
                               registrationHandler);
    }

    @Test
    public void shouldFailToHandleUserInitiatedLogoutWithInvalidDefaultLogoutUri() throws Exception {
        // Given
        setUpForHandleUserInitiatedLogoutCases("/logout");

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter()
                                              .setRequireHttps(false)
                                              .setDefaultLogoutGoto(Expression.valueOf("<??>", String.class));

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then

        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        assertThatSessionIsEmpty();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, loginHandler, next, registrationHandler);
    }

    /************************************************************************************************************/
    /** handleProtectedResource case
    /************************************************************************************************************/
    /** Set up for the oauth2 unit tests. */
    private void setUpForHandleProtectedResourceCases() throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI));
        request.setMethod("GET").setUri(REQUESTED_URI);
        registrations.add(buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME, registrationHandler));
    }

    @Test
    public void shouldHandleProtectedResourceRedirectToAuthorizeEndpointWhenNotAuthorized() throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThatAuthorizationRedirectHandlerProducesRedirect(response);
        verifyZeroInteractions(next, failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldSucceedToHandleProtectedResourceWhenAuthorized() throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(new Response(OK)));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThatTargetAttributesAreSet();
        verify(next).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldSucceedToHandleProtectedResourceByRefreshingTheToken() throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class)))
            // Accessing the resource is unauthorized for the first time due to an invalid access token.
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(UNAUTHORIZED,
                                                                    E_INVALID_TOKEN,
                                                                    ACCESS_TOKEN_HAS_EXPIRED)))
            // Second time, it succeeded to access the protected resource.
            .thenReturn(newResponsePromise(buildOAuth2Response(OK, json(object(field("success", "Access granted"))))));
        // The registration handler provides a new access token when the ClientRegistration#refreshAccessToken is called
        when(registrationHandler.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(buildOAuth2Response(OK, json(object(field("access_token", NEW_ACCESS_TOKEN),
                                                             field("refresh_token", NEW_REFRESH_TOKEN),
                                                             field("expires_in", 1000),
                                                             field("id_token", OAuth2TestUtils.ID_TOKEN))))));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThatTargetAttributesAreSetAndContain(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);

        assertThat(response.getStatus()).isEqualTo(OK);
        verify(next, times(2)).handle(eq(context), any(Request.class));
        verify(registrationHandler).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain);
    }

    @Test
    public void shouldHandleProtectedResourceTriesToRefreshTokenOnlyOnce() throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class)))
            // Accessing the resource is always unauthorized due to an invalid access token.
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(UNAUTHORIZED,
                                                                    E_INVALID_TOKEN,
                                                                    ACCESS_TOKEN_HAS_EXPIRED)));
        // The registration handler provides a new access token when the ClientRegistration#refreshAccessToken is called
        when(registrationHandler.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(buildOAuth2Response(OK,
                                                               json(object(
                                                                     field("access_token", NEW_ACCESS_TOKEN),
                                                                     field("refresh_token", NEW_REFRESH_TOKEN),
                                                                     field("expires_in", 1000),
                                                                     field("id_token", OAuth2TestUtils.ID_TOKEN))))));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThatTargetAttributesAreSetAndContain(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);

        assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        verify(next, times(2)).handle(eq(context), any(Request.class));
        verify(registrationHandler).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain);
    }

    @Test
    public void shouldFailToHandleProtectedResourceWhenRefreshingTokenFails() throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class)))
            // Accessing the resource is unauthorized for the first time due to an invalid access token.
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(UNAUTHORIZED,
                                                                    E_INVALID_TOKEN,
                                                                    ACCESS_TOKEN_HAS_EXPIRED)));
        // The registration handler fails when the ClientRegistration#refreshAccessToken is called
        when(registrationHandler.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(INTERNAL_SERVER_ERROR,
                                                                    E_TEMPORARILY_UNAVAILABLE,
                                                                    "Something bad happens")));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verify(next).handle(eq(context), any(Request.class));
        verify(registrationHandler).handle(eq(context), any(Request.class));
        assertThatTargetAttributesAreSet();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain);
    }

    /**
     * All successful responses are returned without any process.
     * (not a 5xx Server Error or a 4xx Client Error)
     */
    @DataProvider
    private static Object[][] okResourceResponses() {
        return new Object[][] {
            { new Response(OK) },
            { new Response(FOUND) },
            { new Response(MOVED_PERMANENTLY) },
            { new Response(CREATED) } };
    }

    @Test(dataProvider = "okResourceResponses")
    public void shouldHandleProtectedResourceReturnProtectedResourceWithoutRefreshingToken(final Response response)
            throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class)))
        // Accessing the resource returns a non 401/expired token:
                .thenReturn(newResponsePromise(response));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        final Response finalResponse = filter.filter(context, request, next).get();

        // Then
        assertThat(finalResponse.getStatus()).isEqualTo(response.getStatus());
        verify(next).handle(eq(context), any(Request.class));
        assertThatTargetAttributesAreSet();
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    /**
     * All non 401(and all 401 not about refreshing the access token) error responses
     * are handled by the failure handler.
     */
    @DataProvider
    private static Object[][] errorResponsesFromAccessingTheResource() {
        return new Object[][] {
            { new Response(TEAPOT) },
            { new Response(BAD_GATEWAY) },
            { new Response(FORBIDDEN) },
            { new Response(INTERNAL_SERVER_ERROR) },
            { buildOAuth2ErrorResponse(UNAUTHORIZED, E_INVALID_CLIENT, "Invalid client") } };
    }

    @Test(dataProvider = "errorResponsesFromAccessingTheResource")
    public void shouldHandleProtectedResourceOnErrorResponseShouldHandleError(final Response response)
            throws Exception {
        // Given
        setUpForHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class)))
            // Accessing the resource returns a non 401/expired token:
            .thenReturn(newResponsePromise(response));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        setSessionAuthorized();

        // When
        Response finalResponse = filter.filter(context, request, next).get();

        // Then
        verify(failureHandler).handle(eq(context), eq(request));
        assertThat(finalResponse.getStatus()).isEqualTo(failureResponse.getStatus());
        verify(next).handle(eq(context), any(Request.class));
        assertThatTargetAttributesAreSet();
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    /*
     * OPENID USE CASES In these tests, we pretend that the client registration
     * contains the scopes "openid" and "email" This is required to have a
     * response from userinfo endpoint containing the email attribute. See RFC
     * https://openid.net/specs/openid-connect-basic-1_0.html#Scopes
     */

    /** Set up for the oauth2 openid unit tests. */
    private void setUpForOpenIdHandleProtectedResourceCases() throws URISyntaxException {
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI));
        request.setMethod("GET").setUri(REQUESTED_URI);
        final List<String> scopes = asList("openid", "email");
        registrations.add(buildClientRegistrationWithScopes(DEFAULT_OPENID_CLIENT_REGISTRATION_NAME,
                                                            registrationHandler,
                                                            null,
                                                            scopes));
        // Authorize the session for the openid registration with the given scopes
        context.asContext(SessionContext.class)
               .getSession()
               .put("oauth2:http://www.example.com:443/openid",
                    buildAuthorizedOAuth2Session(DEFAULT_OPENID_CLIENT_REGISTRATION_NAME,
                                                 REQUESTED_URI,
                                                 scopes));
    }

    @Test
    public void shouldSucceedToHandleProtectedOpenIdResource() throws Exception {
        // Given
        setUpForOpenIdHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(new Response(OK)));
        when(registrationHandler.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(
                    buildOAuth2Response(OK, json(object(field("email", "janedoe@example.com"))))));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThatTargetAttributesAreSetAndContain(null, null, singletonMap("email", "janedoe@example.com"));
        verify(next).handle(eq(context), any(Request.class));
        verify(registrationHandler).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain);
    }

    @Test
    public void shouldSucceedToHandleProtectedOpenIdResourceByRefreshingToken() throws Exception {
        // Given
        setUpForOpenIdHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(new Response(OK)));
        when(registrationHandler.handle(eq(context), any(Request.class)))
            // First call is an invalid token for accessing the user info endpoint.
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(UNAUTHORIZED,
                                                                    "invalid_token",
                                                                    "The Access Token expired")))
            // Refresh the token
            .thenReturn(newResponsePromise(buildOAuth2Response(OK,
                                                               json(object(
                                                                     field("access_token", NEW_ACCESS_TOKEN),
                                                                     field("refresh_token", NEW_REFRESH_TOKEN),
                                                                     field("expires_in", 1000),
                                                                     field("id_token", OAuth2TestUtils.ID_TOKEN))))))
            .thenReturn(newResponsePromise(
                    buildOAuth2Response(OK, json(object(field("email", "janedoe@example.com"))))));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThatTargetAttributesAreSetAndContain(NEW_ACCESS_TOKEN,
                                                   NEW_REFRESH_TOKEN, singletonMap("email", "janedoe@example.com"));
        verify(next).handle(eq(context), any(Request.class));
        verify(registrationHandler, times(3)).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain);
    }

    @Test
    public void shouldFailToHandleProtectedOpenIdResourceWhenRefreshingTokenFail() throws Exception {
        // Given
        setUpForOpenIdHandleProtectedResourceCases();
        when(next.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(new Response(OK)));
        when(registrationHandler.handle(eq(context), any(Request.class)))
            // Called twice. The second call tries to refresh the token.
            .thenReturn(newResponsePromise(buildOAuth2ErrorResponse(UNAUTHORIZED,
                                                                    "invalid_token",
                                                                    "The Access Token expired")));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        // If an exception occurs when retrieving the user info. The failureHandler manages it.
        // Attributes should not be filled.
        verify(failureHandler).handle(eq(context), eq(request));
        assertThatExceptionAttributeIsSet();
        assertThat(response.getStatus()).isEqualTo(failureResponse.getStatus());
        verify(registrationHandler, times(2)).handle(eq(context), any(Request.class));
        verifyZeroInteractions(discoveryAndDynamicRegistrationChain, next);
    }


    @Test
    public void shouldExpirationFunctionReturnZeroWhenPromiseFailed() throws Exception {
        AsyncFunction<Promise<Map<String, Object>, OAuth2ErrorException>, Duration, Exception> expirationFunction =
                OAuth2ClientFilter.Heaplet.expirationFunction(duration(1, TimeUnit.MINUTES));

        Promise<? extends Duration, ? extends Exception> promiseDuration = expirationFunction.apply(
                Promises.<Map<String, Object>, OAuth2ErrorException>newExceptionPromise(null));

        assertThat(promiseDuration.getOrThrow()).isEqualByComparingTo(Duration.ZERO);
    }

    @Test
    public void shouldExpirationFunctionReturnTheExpirationWhenPromiseFailed() throws Exception {
        Duration oneMinute = duration(1, TimeUnit.MINUTES);
        AsyncFunction<Promise<Map<String, Object>, OAuth2ErrorException>, Duration, Exception> expirationFunction =
                OAuth2ClientFilter.Heaplet.expirationFunction(oneMinute);

        Promise<? extends Duration, ? extends Exception> promiseDuration = expirationFunction.apply(
                Promises.<Map<String, Object>, OAuth2ErrorException>newResultPromise(null));

        assertThat(promiseDuration.getOrThrow()).isEqualByComparingTo(duration(1, TimeUnit.MINUTES));
    }

    private static void assertThatAuthorizationRedirectHandlerProducesRedirect(final Response response) {
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().get("Location").getFirstValue()).contains(
                "response_type=code",
                "client_id=" + DEFAULT_CLIENT_REGISTRATION_NAME,
                "redirect_uri=" + ORIGINAL_URI + "/openid/callback",
                "scope=" + DEFAULT_SCOPE, "state=");
    }

    private void assertThatSessionIsEmpty() {
        SessionContext sessionContext = context.asContext(SessionContext.class);
        assertThat(sessionContext.getSession()).isEmpty();
    }

    private void assertThatTargetAttributesAreSet() {
        assertThatTargetAttributesAreSetAndContain(null, null, null);
    }

    private void assertThatTargetAttributesAreSetAndContain(final String accessToken,
                                                            final String refreshToken) {
        assertThatTargetAttributesAreSetAndContain(accessToken, refreshToken, null);
    }

    @SuppressWarnings("unchecked")
    private void assertThatTargetAttributesAreSetAndContain(final String accessToken,
                                                            final String refreshToken,
                                                            final Map<String, String> userInfo) {
        final Map<String, ?> attributes = getAttributes();
        if (accessToken != null) {
            assertThat(attributes.get("access_token")).isEqualTo(accessToken);
        } else {
            assertThat(attributes.get("access_token")).isEqualTo(OAuth2TestUtils.ID_TOKEN);
        }
        if (refreshToken != null) {
            assertThat(attributes.get("refresh_token")).isEqualTo(refreshToken);
        } else {
            assertThat(attributes.get("refresh_token")).isEqualTo(OAuth2TestUtils.REFRESH_TOKEN);
        }
        assertThat(attributes.get("client_registration")).isNotNull();
        assertThat(attributes.get("client_endpoint")).isEqualTo(REQUESTED_URI);
        assertThat((Long) attributes.get("expires_in")).isGreaterThan(0);
        assertThat((List<String>) attributes.get("scope")).isNotEmpty();
        assertThat(((Map<String, ?>) attributes.get("id_token_claims"))).isNotEmpty();
        if (userInfo != null) {
            assertThatTargetContainsUserInfoAttributes(attributes, userInfo);
        }
    }

    private void assertThatExceptionAttributeIsSet() {
        assertThat(getAttributes().get("exception")).isNotNull().isInstanceOf(Exception.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getAttributes() {
        return (Map<String, ?>) sessionContext.asContext(AttributesContext.class)
                                              .getAttributes()
                                              .get(TARGET);
    }

    @SuppressWarnings("unchecked")
    private static void assertThatTargetContainsUserInfoAttributes(final Map<String, ?> attributes,
                                                                   final Map<String, String> expectedUserInfoValues) {
        final Map<String, ?> userInfo = (Map<String, ?>) attributes.get("user_info");
        for (final Map.Entry<String, String> userInfoAttribute : expectedUserInfoValues.entrySet()) {
            assertThat(userInfo.get(userInfoAttribute.getKey())).isEqualTo(userInfoAttribute.getValue());
        }
        assertThat(userInfo).hasSameSizeAs(expectedUserInfoValues);
    }

    private static Response buildOAuth2ErrorResponse(final Status status,
                                                     final String oauth2ErrorCode,
                                                     final String oauth2ErrorMessage) {
        final Response response = new Response(status);
        response.getHeaders().put(OAuth2BearerWWWAuthenticateHeader.NAME,
                                  format("Bearer realm=\"example\", error=\"%s\", error_description=\"%s\"",
                                         oauth2ErrorCode, oauth2ErrorMessage));
        return response;
    }

    private static Response buildOAuth2Response(final Status status,
                                                final JsonValue entity) {
        final Response response = new Response(status);
        response.setEntity(entity.asMap());
        return response;
    }

    private void setSessionAuthorized() {
        context.asContext(SessionContext.class)
               .getSession()
               .put("oauth2:http://www.example.com:443/openid",
                    buildAuthorizedOAuth2Session(DEFAULT_CLIENT_REGISTRATION_NAME, REQUESTED_URI));
    }

    private OAuth2ClientFilter buildOAuth2ClientFilter() throws ExpressionException {
        final PerItemEvictionStrategyCache<String, Promise<Map<String, Object>, OAuth2ErrorException>> cache
            = new PerItemEvictionStrategyCache<>(newSingleThreadScheduledExecutor(), Duration.ZERO);
        final OAuth2ClientFilter filter = new OAuth2ClientFilter(registrations,
                                                                 cache,
                                                                 time,
                                                                 discoveryAndDynamicRegistrationChain,
                                                                 Expression.valueOf(DEFAULT_CLIENT_ENDPOINT,
                                                                                    String.class));
        filter.setFailureHandler(failureHandler);
        filter.setTarget(LeftValueExpression.valueOf("${attributes.openid}", Object.class));
        return filter;
    }

    private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("myFailureHandler", failureHandler);
        heap.put("myLoginHandler", loginHandler);
        heap.put(DEFAULT_CLIENT_REGISTRATION_NAME, buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME,
                                                                           registrationHandler,
                                                                           DEFAULT_SCOPE));
        return heap;
    }
}
