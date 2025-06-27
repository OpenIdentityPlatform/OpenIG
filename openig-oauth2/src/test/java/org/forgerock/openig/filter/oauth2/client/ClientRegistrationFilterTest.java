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
import static org.forgerock.http.protocol.Status.BAD_REQUEST;
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.ClientRegistration.CLIENT_REG_KEY;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ClientRegistrationFilterTest {

    private static final String REGISTRATION_ENDPOINT = "/openam/oauth2/connect/register";
    private static final String SAMPLE_URI = "http://www.example.com:8089";

    private AttributesContext context;
    private ClientRegistrationRepository registrations;

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Handler handler;

    @Mock
    private Handler next;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        context = new AttributesContext(new RootContext());
        registrations = spy(new ClientRegistrationRepository());
    }

    @Test
    public void shouldFailToPerformDynamicRegistrationWhenIssuerIsMissing() throws Exception {
        // given
        setAttributesIssuerKey(null);

        final ClientRegistrationFilter crf = buildClientRegistrationFilter();

        // when
        final Response response = crf.filter(context, new Request(), next).get();

        // then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        final Exception cause = response.getCause();
        assertThat(cause).isInstanceOf(RegistrationException.class);
        assertThat(cause.getMessage()).contains("Cannot retrieve issuer from the context");
    }

    @Test
    public void shouldFailToPerformDynamicRegistrationWhenMissingRedirectUris() throws Exception {
        // given
        setAttributesIssuerKey(new Issuer("myIssuer", issuerConfigWithAllRequestedEndpoints()));
        final JsonValue invalidConfig = json(object(
                                                field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                                                field("scopes", array("openid"))));

        final ClientRegistrationFilter crf = new ClientRegistrationFilter(registrations,
                                                                          handler,
                                                                          invalidConfig
        );

        // when
        final Response response = crf.filter(context, new Request(), next).get();

        // then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        assertThat(response.getCause().getMessage()).contains("'redirect_uris' should be defined");
    }

    @Test
    public void shouldFailToPerformDynamicRegistrationWhenIssuerHasNoRegistrationEndpoint() throws Exception {
        // given
        setAttributesIssuerKey(new Issuer("myIssuer", issuerConfigWithNoRegistrationEndpoint()));
        final ClientRegistrationFilter crf = buildClientRegistrationFilter();

        // when
        final Response response = crf.filter(context, new Request(), next).get();

        // then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        Exception cause = response.getCause();
        assertThat(cause).isInstanceOf(RegistrationException.class);
        assertThat(cause.getMessage()).contains("Registration is not supported by the issuer");
    }

    @Test
    public void shouldPerformDynamicRegistration() throws Exception {
        // given
        final ClientRegistrationFilter crf = buildClientRegistrationFilter();
        final Response response = new Response(CREATED);
        response.setEntity(json(object()));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        crf.performDynamicClientRegistration(context,
                                             getMetadata(),
                                             new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));

        // then
        verify(handler).handle(eq(context), captor.capture());
        Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEntity().toString()).containsSequence("redirect_uris", "contact", "scopes");
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailToPerformDynamicRegistrationWhenStatusCodeResponseIsDifferentFromCreated() throws Exception {
        // given
        final ClientRegistrationFilter crf = buildClientRegistrationFilter();
        final Response response = new Response(BAD_REQUEST);
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        crf.performDynamicClientRegistration(context,
                                             getMetadata(),
                                             new URI(SAMPLE_URI + REGISTRATION_ENDPOINT)).getOrThrow();
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailToPerformDynamicRegistrationWhenResponseHasInvalidResponseContent() throws Exception {
        // given
        final ClientRegistrationFilter crf = buildClientRegistrationFilter();
        final Response response = new Response(CREATED);
        response.setEntity(array("invalid", "content"));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        crf.performDynamicClientRegistration(context,
                                             getMetadata(),
                                             new URI(SAMPLE_URI + REGISTRATION_ENDPOINT)).getOrThrow();
    }

    @Test
    public void shouldPerformDynamicRegistrationTwiceReusingCreatedRegistration() throws Exception {
        // Given
        setAttributesIssuerKey(new Issuer("myIssuer", issuerConfigWithAllRequestedEndpoints()));

        final ClientRegistrationFilter crf =  buildClientRegistrationFilter();
        when(handler.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(performedClientRegistration()))
            // should not happen
            .thenReturn(newResponsePromise(new Response(INTERNAL_SERVER_ERROR)));

        when(next.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(new Response(OK)));

        // When
        Response response = crf.filter(context, new Request(), next).get();

        // Then, the client registration has created an oauth2-openid client in the OpenID Provider.
        verify(handler).handle(eq(context), any(Request.class));
        verify(next).handle(eq(context), any(Request.class));
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(context.getAttributes().get(CLIENT_REG_KEY))
            .isInstanceOf(ClientRegistration.class).isEqualTo(registrations.findDefault());

        // If the dynamic client registration is called twice with the same parameters:
        response = crf.filter(context, new Request(), next).get();

        // Then, the client registration is not created but reused instead.
        verify(next, times(2)).handle(eq(context), any(Request.class));
        verifyNoMoreInteractions(handler);
        assertThat(response.getStatus()).isEqualTo(OK);
        verify(registrations).add(any(ClientRegistration.class));
    }

    private ClientRegistrationFilter buildClientRegistrationFilter() {
        return new ClientRegistrationFilter(registrations, handler, getMetadata());
    }

    private JsonValue getMetadata() {
        return json(object(
                        field("redirect_uris", array("https://client.example.org/callback")),
                        field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                        field("scopes", array("openid"))));
    }

    private JsonValue issuerConfigWithNoRegistrationEndpoint() {
        return json(object(
                        field("authorizeEndpoint", "http://www.example.com:8089/openam/oauth2/authorize"),
                        field("tokenEndpoint", "http://www.example.com:8089/openam/oauth2/access_token"),
                        field("userInfoEndpoint", "http://www.example.com:8089/openam/oauth2/userinfo")));
    }

    private JsonValue issuerConfigWithAllRequestedEndpoints() {
        return issuerConfigWithNoRegistrationEndpoint()
                .put("registrationEndpoint", "http://www.example.com:8089/openam/oauth2/connect/register");
    }

    private static Response performedClientRegistration() {
        final Response response = new Response(CREATED);
        response.setEntity(clientRegistrationOnOpenAMAsJsonResponse());
        return response;
    }

    private static JsonValue clientRegistrationOnOpenAMAsJsonResponse() {
        // This is the response given by OpenAM when the dynamic registration is successful.
        return json(object(
                field("default_max_age_enabled", false),
                field("subject_type", "public"),
                field("default_max_age", 1),
                field("application_type", "web"),
                field("jwt_token_lifetime", 0),
                field("registration_client_uri",
                        "http://www.example.com:8089/openam/oauth2/connect/register?client_id=77581f2e-d909-..."),
                field("client_type", "Confidential"),
                field("redirect_uris", array("http://localhost:8082/openid/callback")),
                field("client_id", "77581f2e-d909-4018-963b-22ae9d34a8d8"),
                field("token_endpoint_auth_method", "client_secret_basic"),
                field("public_key_selector", "x509"),
                field("client_secret_expires_at", 0),
                field("access_token_lifetime", 0),
                field("refresh_token_lifetime", 0),
                field("authorization_code_lifetime", 0),
                field("scopes", array("openid")),
                field("client_secret", "023e9236-d919-4c03-bab0-36ffd83ff2d6"),
                field("client_name", "My App"),
                field("contacts", array("ve7jtb@example.org", "mary@example.org")),
                field("id_token_signed_response_alg", "HS256"),
                field("response_types", array("code"))));
    }

    private void setAttributesIssuerKey(final Issuer issuer) {
        context.getAttributes().put(ISSUER_KEY, issuer);
    }
}
