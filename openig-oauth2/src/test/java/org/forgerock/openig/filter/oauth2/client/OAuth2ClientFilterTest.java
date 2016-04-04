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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.FOUND;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildAuthorizedOAuth2Session;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.buildClientRegistration;
import static org.forgerock.openig.filter.oauth2.client.OAuth2TestUtils.newSession;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;
import java.util.Collections;

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
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2ClientFilterTest {

    private static final String DEFAULT_CLIENT_REGISTRATION_NAME = "openam";
    private static final String DEFAULT_CLIENT_ENDPOINT = "/openid";
    private static final String ORIGINAL_URI = "http://www.example.com:443";
    private static final String REQUESTED_URI = "http://www.example.com/myapp/endpoint";

    private AttributesContext attributesContext;
    private ClientRegistrationRepository registrations;
    private Context context;
    private Request request;
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
        context = new UriRouterContext(sessionContext,
                                       null,
                                       null,
                                       Collections.<String, String>emptyMap(),
                                       new URI(ORIGINAL_URI));
        registrations = new ClientRegistrationRepository();
        request = new Request();
        request.setMethod("GET").setUri(REQUESTED_URI);
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
    /** handleProtectedResource case
    /************************************************************************************************************/

    @Test
    public void shouldFailToHandleProtectedResourceWhenNoClientRegistrationIsSpecified() throws Exception {
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        final Response response = filter.filter(context, request, next).get();

        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
        verifyZeroInteractions(next, failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldHandleProtectedResourceRedirectToAuthorizeEndpointWhenNotAuthorized() throws Exception {
        // Given
        registrations.add(buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME, registrationHandler));
        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(FOUND);
        assertThat(response.getHeaders().get("Location").getFirstValue()).contains(
                "response_type=code",
                "client_id=" + DEFAULT_CLIENT_REGISTRATION_NAME,
                "redirect_uri=" + ORIGINAL_URI + "/openid/callback",
                "scope=openid", "state=");
        verifyZeroInteractions(next, failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    @Test
    public void shouldSucceedToHandleProtectedResourceWhenAuthorized() throws Exception {
        // Given
        when(next.handle(eq(context), any(Request.class)))
            .thenReturn(newResponsePromise(new Response(OK)));
        registrations.add(buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME, registrationHandler));

        final OAuth2ClientFilter filter = buildOAuth2ClientFilter();
        filter.setTarget(Expression.valueOf("${attributes.openid}", Object.class));

        context.asContext(SessionContext.class)
               .getSession()
               .put("oauth2:http://www.example.com:443/openid",
                    buildAuthorizedOAuth2Session(DEFAULT_CLIENT_REGISTRATION_NAME, REQUESTED_URI));

        // When
        final Response response = filter.filter(context, request, next).get();

        // Then
        assertThat(response.getStatus()).isEqualTo(OK);
        verify(next).handle(eq(context), any(Request.class));
        verifyZeroInteractions(failureHandler, discoveryAndDynamicRegistrationChain, registrationHandler);
    }

    private static Response buildOAuth2Response(final Status status,
                                                final JsonValue entity) {
        final Response response = new Response(status);
        response.setEntity(entity.asMap());
        return response;
    }

    private OAuth2ClientFilter buildOAuth2ClientFilter() throws ExpressionException {
        final OAuth2ClientFilter filter = new OAuth2ClientFilter(registrations,
                                                                 time,
                                                                 discoveryAndDynamicRegistrationChain,
                                                                 Expression.valueOf(DEFAULT_CLIENT_ENDPOINT,
                                                                                    String.class));
        filter.setFailureHandler(failureHandler);
        return filter;
    }

    private HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("myFailureHandler", failureHandler);
        heap.put("myLoginHandler", loginHandler);
        heap.put(DEFAULT_CLIENT_REGISTRATION_NAME, buildClientRegistration(DEFAULT_CLIENT_REGISTRATION_NAME,
                                                                           registrationHandler));
        return heap;
    }
}
