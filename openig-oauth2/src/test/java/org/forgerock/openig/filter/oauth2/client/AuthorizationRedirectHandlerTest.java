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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.util.StringUtil.join;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuthorizationRedirectHandlerTest {

    private static final String CLIENT_ENDPOINT = "/openid";
    private static final String OPENAM_BASE_OAUTH2 = "http://www.example.com:8089/openam/oauth2";
    private static final String ORIGINAL_URI = "http://www.ig.original.com:8082";

    private Context context;
    private Request request;
    private SessionContext sessionContext;
    private AttributesContext attributesContext;

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        sessionContext = new SessionContext(new RootContext(), new SimpleMapSession());
        attributesContext = new AttributesContext(sessionContext);
        context = new UriRouterContext(attributesContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       URI.create(ORIGINAL_URI));
        attributesContext.getAttributes().put("clientEndpoint", CLIENT_ENDPOINT);
        request = new Request();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailWhenProvidedEndpointIsNull() {
        new AuthorizationRedirectHandler(time, null);
    }

    @Test
    public void shouldFailWhenProvidedEndpointIsInvalid() throws Exception {
        // Given
        attributesContext.getAttributes().put("invalidClientEndpoint", "[fails");

        // When
        Expression<String> expression = Expression.valueOf("${attributes.invalidClientEndpoint}",
                                                           String.class);
        final AuthorizationRedirectHandler handler =
                new AuthorizationRedirectHandler(time,
                                                 expression,
                                                 buildClientRegistration());
        // Then
        final Response response = handler.handle(context, request).get();
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
    }

    @Test
    public void shouldFailWhenClientRegistrationIsNotProvided() throws Exception {
        // Given
        request.setUri("http://localhost:8082/openid/login?clientRegistration=myAppRegistration"
                                                       + "&goto=http://localhost:8082/openid/");
        final AuthorizationRedirectHandler handler = buildAuthorizationRedirectHandler(null);

        // When
        final Response response = handler.handle(context, request).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).contains("The selected client or its issuer is null",
                                                              "Authorization redirect aborted");
    }

    @Test
    public void shouldFailWhenClientRegistrationDoesNotContainIssuer() throws Exception {
        // Given
        request.setUri("http://localhost:8082/openid/login?clientRegistration=myAppRegistration"
                                                       + "&goto=http://localhost:8082/openid/");
        final ClientRegistration registration = buildClientRegistrationWithIssuer(false);
        final AuthorizationRedirectHandler handler = buildAuthorizationRedirectHandler(registration);

        // When
        final Response response = handler.handle(context, request).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).contains("The selected client or its issuer is null",
                                                              "Authorization redirect aborted");
    }

    @Test
    public void shouldSucceedToGenerateLocationHeader() throws Exception {
        // Given
        request.setUri("http://localhost:8082/openid/login?clientRegistration=myAppRegistration"
                                                       + "&goto=http://localhost:8082/openid/");
        final ClientRegistration registration = buildClientRegistration();
        final AuthorizationRedirectHandler handler = buildAuthorizationRedirectHandler(registration);

        // When
        final Response response = handler.handle(context, request).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(FOUND);
        final String locationHeader = response.getHeaders().get("Location").getFirstValue();
        assertThat(locationHeader).contains(OPENAM_BASE_OAUTH2 + "/authorize?",
                                            "redirect_uri=" + ORIGINAL_URI + CLIENT_ENDPOINT + "/callback",
                                            "response_type=code",
                                            "client_id=" + registration.getClientId(),
                                            "scope=" + join(" ", registration.getScopes()),
                                            "state=");
        assertThat(sessionContext.getSession().get("oauth2:" + ORIGINAL_URI + CLIENT_ENDPOINT)).isNotNull();
    }

    private AuthorizationRedirectHandler buildAuthorizationRedirectHandler(final ClientRegistration registration)
            throws Exception {
        return new AuthorizationRedirectHandler(time,
                                                Expression.valueOf("${attributes.clientEndpoint}",
                                                                   String.class),
                                                registration);
    }

    private ClientRegistration buildClientRegistration() throws Exception {
        return buildClientRegistrationWithIssuer(true);
    }

    private ClientRegistration buildClientRegistrationWithIssuer(final boolean containIssuer) throws Exception {
        return new ClientRegistration("myAppRegistered",
                                      buildClientRegistrationConfiguration(),
                                      containIssuer ? buildIssuerWithAllRequestedEndpoints() : null,
                                      mock(Handler.class));
    }

    private JsonValue buildClientRegistrationConfiguration() {
        return json(object(field("clientId", "myAppRegistration"),
                           field("clientSecret", "password"),
                           field("issuer", "myIssuer"),
                           field("scopes", array("openid", "profile"))));
    }

    private Issuer buildIssuerWithAllRequestedEndpoints() {
        final JsonValue issuerConfig = json(object(
            field("authorizeEndpoint", OPENAM_BASE_OAUTH2 + "/authorize"),
            field("registrationEndpoint", OPENAM_BASE_OAUTH2 + "/connect/register"),
            field("tokenEndpoint", OPENAM_BASE_OAUTH2 + "/access_token"),
            field("userInfoEndpoint", OPENAM_BASE_OAUTH2 + "/userinfo")));
        return new Issuer("myIssuer", issuerConfig);
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException {
            // Nothing to do.
        }
    }
}
