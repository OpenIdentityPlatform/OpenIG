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
package org.forgerock.openig.filter.oauth2.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.ISSUER_URI;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.TOKEN_ENDPOINT;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.USER_INFO_ENDPOINT;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildIssuerWithoutWellKnownEndpoint;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.TimeService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Unit tests for the ClientRegistration class. */
@SuppressWarnings("javadoc")
public class ClientRegistrationTest {

    private Context context;
    private OAuth2Session session;

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Handler handler;

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        context = new RootContext();
        session = OAuth2Session.stateNew(time);
    }

    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            /* Minimal configuration. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("clientSecret", "password"),
                    field("issuer", "myIssuer"),
                    field("scopes", array("openid")))) },
            /* With token end point using POST. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("clientSecret", "password"),
                    field("scopes", array("openid", "profile", "email", "address", "phone", "offline_access")),
                    field("issuer", "myIssuer"),
                    field("tokenEndpointUseBasicAuth", false))) } };
    }

    @DataProvider
    private static Object[][] missingRequiredAttributes() {
        return new Object[][] {
            /* Missing clientId. */
            { json(object(
                    field("clientSecret", "password"),
                    field("scopes", array("openid")),
                    field("issuer", "myIssuer"))) },
            /* Missing clientSecret. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("scopes", array("openid")),
                    field("issuer", "myIssuer"))) },
            /* Missing issuer. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("clientSecret", "password"),
                    field("scopes", array("openid")),
                    field("issuer", "notDeclaredIssuer"))) }};
    }

    @DataProvider
    private static Object[][] errorResponseStatus() {
        return new Status[][] {
            { Status.BAD_REQUEST },
            { Status.BAD_GATEWAY } };
    }

    @Test(dataProvider = "missingRequiredAttributes", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        final ClientRegistration.Heaplet heaplet = new ClientRegistration.Heaplet();
        heaplet.create(Name.of("myClientRegistration"), config, buildDefaultHeap());
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws HeapException, Exception {
        final ClientRegistration.Heaplet heaplet = new ClientRegistration.Heaplet();
        final ClientRegistration cr = (ClientRegistration) heaplet.create(Name.of("myClientRegistration"),
                                                                          config,
                                                                          buildDefaultHeap());
        assertThat(cr.getClientId()).isEqualTo("OpenIG");
        assertThat(cr.getScopes()).contains("openid");
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldReturnInlinedConfiguration(final JsonValue config) throws Exception {
        final ClientRegistration.Heaplet heaplet = new ClientRegistration.Heaplet();
        final ClientRegistration cr = (ClientRegistration) heaplet.create(Name.of("myClientRegistration"),
                                                                          config,
                                                                          buildDefaultHeap());

        assertThat(cr.getName()).isEqualTo("myClientRegistration");
        assertThat(cr.getClientId()).isEqualTo("OpenIG");
        assertThat(cr.getScopes()).contains("openid");
    }

    @Test
    public void shouldGetAccessToken() throws Exception {
        // given
        final String code = "sampleAuthorizationCodeForTestOnly";
        final String callbackUri = "shouldBeACallbackUri";
        final ClientRegistration cr = buildClientRegistration();
        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(json(object(field("access_token", "ae32f"))));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        cr.getAccessToken(context, code, callbackUri);

        // then
        verify(handler).handle(eq(context), captor.capture());
        final Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUri().toASCIIString()).isEqualTo(ISSUER_URI + TOKEN_ENDPOINT);
        assertThat(request.getEntity().getString()).contains("grant_type=authorization_code",
                                                             "redirect_uri=" + callbackUri, "code=" + code);
    }

    @Test(dataProvider = "errorResponseStatus", expectedExceptions = OAuth2ErrorException.class)
    public void shouldFailToGetAccessTokenWhenReceiveErrorResponse(final Status errorResponseStatus) throws Exception {

        final Response response = new Response();
        response.setStatus(errorResponseStatus);
        response.setEntity(json(object(field("error", "Generated by tests"))));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        buildClientRegistration().getAccessToken(context, "code", "callbackUri").getOrThrow();
    }

    @Test
    public void shouldGetRefreshToken() throws Exception {
        // given
        final ClientRegistration clientRegistration = buildClientRegistration();
        Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(json(object(field("access_token", "ae32f"))));
        when(handler.handle(eq(context), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));

        // when
        clientRegistration.refreshAccessToken(context, session);

        // then
        verify(handler).handle(eq(context), captor.capture());
        Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUri().toASCIIString()).isEqualTo(ISSUER_URI + TOKEN_ENDPOINT);
        assertThat(request.getEntity().getString()).contains("grant_type=refresh_token");
    }

    @Test(dataProvider = "errorResponseStatus", expectedExceptions = OAuth2ErrorException.class)
    public void shouldFailToGetRefreshTokenWhenReceiveErrorResponse(final Status errorResponseStatus) throws Exception {

        Response response = new Response();
        response.setStatus(errorResponseStatus);
        response.setEntity(setErrorEntity());
        when(handler.handle(eq(context), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));

        buildClientRegistration().refreshAccessToken(context, session).getOrThrow();
    }

    @Test
    public void shouldGetUserInfo() throws Exception {
        // given
        final ClientRegistration clientRegistration = buildClientRegistration();
        Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(json(object(field("name", "bjensen"))));
        when(handler.handle(eq(context), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));

        // when
        clientRegistration.getUserInfo(context, session);

        // then
        verify(handler).handle(eq(context), captor.capture());
        Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUri().toASCIIString()).isEqualTo(ISSUER_URI + USER_INFO_ENDPOINT);
        assertThat(request.getHeaders().get("Authorization").getValues()).isNotEmpty();
    }

    @Test(expectedExceptions = OAuth2ErrorException.class,
            expectedExceptionsMessageRegExp = "error=\"server_error\"")
    public void shouldFailToGetUserInfoWhenReceiveErrorResponse() throws Exception {

        Response response = new Response();
        response.setStatus(Status.TEAPOT);
        response.setEntity(setErrorEntity());
        when(handler.handle(eq(context), any(Request.class)))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(response));

        buildClientRegistration().getUserInfo(context, session).getOrThrow();
    }

    private ClientRegistration buildClientRegistration() throws Exception {
        final JsonValue config = json(object(field("clientId", "OpenIG"),
                                             field("clientSecret", "password"),
                                             field("issuer", "myIssuer"),
                                             field("scopes", array("openid"))));
        return new ClientRegistration(null,
                                      config,
                                      buildIssuerWithoutWellKnownEndpoint("myIssuer"),
                                      handler);
    }

    private static HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("myIssuer", buildIssuerWithoutWellKnownEndpoint("myIssuer"));
        return heap;
    }

    private static JsonValue setErrorEntity() {
        return json(object(field("error", "Generated by tests")));
    }
}
