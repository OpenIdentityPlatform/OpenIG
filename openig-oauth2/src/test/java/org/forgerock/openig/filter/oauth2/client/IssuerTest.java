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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.net.URI.create;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.Name;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Unit tests for the Issuer class. */
@SuppressWarnings("javadoc")
public class IssuerTest {
    private static final String AUTHORIZE_ENDPOINT = "/openam/oauth2/authorize";
    private static final String REGISTRATION_ENDPOINT = "/openam/oauth2/connect/register";
    private static final String TOKEN_ENDPOINT = "/openam/oauth2/access_token";
    private static final String USER_INFO_ENDPOINT = "/openam/oauth2/userinfo";
    private static final String WELLKNOWN_ENDPOINT = "/openam/oauth2/.well-known/openid-configuration";
    private static final String SAMPLE_URI = "http://www.example.com:8089";

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
    }

    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("authorizeEndpoint", "http://www.example.com:8089/openam/oauth2/authorize"),
                    field("tokenEndpoint", "http://www.example.com:8089/openam/oauth2/access_token"))) },
            { json(object(
                    field("authorizeEndpoint", "http://www.example.com:8089/openam/oauth2/authorize"),
                    field("registrationEndpoint", "http://www.example.com:8089/openam/oauth2/connect/register"),
                    field("tokenEndpoint", "http://www.example.com:8089/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8089/openam/oauth2/userinfo"))) } };
    }

    @DataProvider
    private Object[][] missingRequiredAttributes() {
        return new Object[][] {
            /* Missing authorizeEndpoint. */
            { json(object(
                    field("name", "openam"),
                    field("tokenEndpoint", "http://www.example.com:8089/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8089/openam/oauth2/userinfo"))) },
            /* Missing tokenInfoEndpoint. */
            { json(object(
                    field("authorizeEndpoint", "http://www.example.com:8089/openam/oauth2/authorize"),
                    field("registrationEndpoint", "http://www.example.com:8089/openam/oauth2/connect/register"),
                    field("userInfoEndpoint", "http://www.example.com:8089/openam/oauth2/userinfo"))) } };
    }

    @Test(dataProvider = "missingRequiredAttributes", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        final Issuer.Heaplet heaplet = new Issuer.Heaplet();
        heaplet.create(Name.of("openam"), config, buildDefaultHeap());
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final Issuer.Heaplet heaplet = new Issuer.Heaplet();
        final Issuer issuer = (Issuer) heaplet.create(Name.of("myIssuer"), config, buildDefaultHeap());
        assertThat(issuer.getName()).isEqualTo("myIssuer");
        assertThat(issuer.getAuthorizeEndpoint().toString()).isEqualTo(SAMPLE_URI + AUTHORIZE_ENDPOINT);
        assertThat(issuer.getTokenEndpoint().toString()).isEqualTo(SAMPLE_URI + TOKEN_ENDPOINT);
    }

    @Test
    public void shouldCreateIssuer() throws Exception {
        final Issuer issuer = new Issuer("myIssuer", buildJsonConfig());
        assertThat(issuer.getAuthorizeEndpoint()).isEqualTo(create(SAMPLE_URI + AUTHORIZE_ENDPOINT));
        assertThat(issuer.getRegistrationEndpoint()).isEqualTo(create(SAMPLE_URI + REGISTRATION_ENDPOINT));
        assertThat(issuer.getTokenEndpoint()).isEqualTo(create(SAMPLE_URI + TOKEN_ENDPOINT));
        assertThat(issuer.getUserInfoEndpoint()).isEqualTo(create(SAMPLE_URI + USER_INFO_ENDPOINT));
        assertThat(issuer.getWellKnownEndpoint()).isEqualTo(create(SAMPLE_URI + WELLKNOWN_ENDPOINT));
    }

    @Test
    public void shouldPerformBuildWithWellKnownUriProvided() throws Exception {
        // given
        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(getValidWellKnownOpenIdConfigurationResponse());

        final URI wellKnownUri = new URI(SAMPLE_URI + WELLKNOWN_ENDPOINT);
        when(handler.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        final Issuer issuer = Issuer.build("myIssuer", wellKnownUri, null, handler);

        // then
        verify(handler).handle(any(Context.class), captor.capture());
        final Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUri().toString()).isEqualTo(wellKnownUri.toString());
        assertThat(issuer.getName()).isEqualTo("myIssuer");
        assertThat(issuer.getAuthorizeEndpoint()).isEqualTo(new URI(SAMPLE_URI + AUTHORIZE_ENDPOINT));
        assertThat(issuer.getRegistrationEndpoint()).isEqualTo(new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
        assertThat(issuer.getTokenEndpoint()).isEqualTo(new URI(SAMPLE_URI + TOKEN_ENDPOINT));
        assertThat(issuer.getUserInfoEndpoint()).isEqualTo(new URI(SAMPLE_URI + USER_INFO_ENDPOINT));
        assertThat(issuer.getSupportedDomains()).isEmpty();
    }

    @Test
    public void shouldPerformBuildWithWellKnownUriProvidedAndContainSupportedDomains() throws Exception {
        // given
        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(getValidWellKnownOpenIdConfigurationResponse());
        when(handler.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(response));
        final URI wellKnownUri = new URI(SAMPLE_URI + WELLKNOWN_ENDPOINT);

        // when
        final Issuer issuer = Issuer.build("myIssuer",
                                           wellKnownUri,
                                           asList("openam.com", "openam.example.com"),
                                           handler);

        // then
        verify(handler).handle(any(Context.class), captor.capture());
        final Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUri().toString()).isEqualTo(wellKnownUri.toString());
        assertThat(issuer.getName()).isEqualTo("myIssuer");
        assertThat(issuer.getAuthorizeEndpoint()).isEqualTo(new URI(SAMPLE_URI + AUTHORIZE_ENDPOINT));
        assertThat(issuer.getRegistrationEndpoint()).isEqualTo(new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
        assertThat(issuer.getTokenEndpoint()).isEqualTo(new URI(SAMPLE_URI + TOKEN_ENDPOINT));
        assertThat(issuer.getUserInfoEndpoint()).isEqualTo(new URI(SAMPLE_URI + USER_INFO_ENDPOINT));
        assertThat(issuer.getSupportedDomains()).hasSize(2);
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void shouldFailToPerformBuildWithWellKnownUriProvidedCausedByInvalidResponse() throws Exception {
        // given
        final Response response = new Response();
        response.setStatus(Status.OK);
        response.setEntity(json(object(field("invalid", "response"))));
        when(handler.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(response));

        Issuer.build("myIssuer", new URI(SAMPLE_URI + WELLKNOWN_ENDPOINT), null, handler);
    }

    @Test(expectedExceptions = DiscoveryException.class)
    public void shouldFailToPerformBuildWithWellKnownUriProvidedCausedByTeapotResponse() throws Exception {
        // given
        final Response response = new Response();
        response.setStatus(Status.TEAPOT);
        response.setEntity(getValidWellKnownOpenIdConfigurationResponse());
        when(handler.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(response));

        Issuer.build("myIssuer", new URI(SAMPLE_URI + WELLKNOWN_ENDPOINT), null, handler);
    }

    private final JsonValue getValidWellKnownOpenIdConfigurationResponse() {
        return json(object(
                        field("authorizeEndpoint", SAMPLE_URI + AUTHORIZE_ENDPOINT),
                        field("registrationEndpoint", SAMPLE_URI + REGISTRATION_ENDPOINT),
                        field("tokenEndpoint", SAMPLE_URI + TOKEN_ENDPOINT),
                        field("userInfoEndpoint", SAMPLE_URI + USER_INFO_ENDPOINT)));
    }

    private final JsonValue buildJsonConfig() {
        return json(object(
                    field("authorizeEndpoint", "http://www.example.com:8089/openam/oauth2/authorize"),
                    field("registrationEndpoint", "http://www.example.com:8089/openam/oauth2/connect/register"),
                    field("tokenEndpoint", "http://www.example.com:8089/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8089/openam/oauth2/userinfo"),
                    field("wellKnownEndpoint",
                            "http://www.example.com:8089/openam/oauth2/.well-known/openid-configuration")));
    }
}
