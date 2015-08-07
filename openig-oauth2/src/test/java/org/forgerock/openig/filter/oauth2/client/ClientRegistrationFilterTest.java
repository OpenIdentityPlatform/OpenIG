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
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.HeapUtilsTest.buildDefaultHeap;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Unit tests for the dynamic registration filter class. */
@SuppressWarnings("javadoc")
public class ClientRegistrationFilterTest {
    private Exchange exchange;
    private static final String SAMPLE_URI = "http://www.example.com:8089";
    private static final String REGISTRATION_ENDPOINT = "/openam/oauth2/connect/register";

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        exchange = new Exchange(null, new URI("path"));
    }

    @DataProvider
    private Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("redirectUris", array("https://client.example.org/callback",
                                                "https://client.example.org/callback")))) },
            { json(object(
                    field("redirectUris", array("https://client.example.org/callback")),
                    field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                    field("scopes", array("openid")))) },
            { json(object(
                    field("redirect_uris", array("https://client.example.org/callback")),
                    field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                    field("token_endpoint_auth_method", "client_secret_basic"),
                    field("scopes", array("openid")))) }};
    }

    @DataProvider
    private Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing clientId. */
            { json(object(
                    field("redirectUriz", array("https://client.example.org/callback")))) },
            /* Redirect uri is not a list. */
            { json(object(
                    field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                    field("scopes", array("openid")),
                    field("redirect_uris", "https://client.example.org/callback"))) } };
    }

    @Test(dataProvider = "invalidConfigurations", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapletWhenRequiredAttributeIsMissing(final JsonValue config) throws Exception {
        final ClientRegistrationFilter.Heaplet heaplet = new ClientRegistrationFilter.Heaplet();
        heaplet.create(Name.of("myDynamicRegistrationFilter"), config, buildDefaultHeap());
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws HeapException, Exception {
        final ClientRegistrationFilter.Heaplet heaplet = new ClientRegistrationFilter.Heaplet();
        final ClientRegistrationFilter drf = (ClientRegistrationFilter)
                heaplet.create(Name.of("myClientRegistration"),
                              config,
                              buildDefaultHeap());
        assertThat(drf).isNotNull();
    }

    @Test
    public void shouldPerformDynamicRegistration() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildFilter();
        final Response response = new Response();
        response.setStatus(Status.CREATED);
        when(handler.handle(eq(exchange), any(Request.class))).thenReturn(newResponsePromise(response));
        // when
        drf.performDynamicClientRegistration(exchange, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
        // then
        verify(handler).handle(eq(exchange), captor.capture());
        Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEntity().toString()).containsSequence("redirectUris", "contact", "scopes");
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailWhenStatusCodeResponseIsDifferentFromCreated() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildFilter();
        final Response response = new Response();
        response.setStatus(Status.BAD_REQUEST);
        when(handler.handle(eq(exchange), any(Request.class))).thenReturn(newResponsePromise(response));
        // when
        drf.performDynamicClientRegistration(exchange, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailWhenResponseHasInvalidResponseContent() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildFilter();
        final Response response = new Response();
        response.setStatus(Status.CREATED);
        response.setEntity(array("invalid", "content"));
        when(handler.handle(eq(exchange), any(Request.class))).thenReturn(newResponsePromise(response));
        // when
        drf.performDynamicClientRegistration(exchange, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
    }

    private ClientRegistrationFilter buildFilter() throws HeapException, Exception {
        return new ClientRegistrationFilter(handler, getFilterConfig(), buildDefaultHeap());
    }

    private JsonValue getFilterConfig() {
        return json(object(
                        field("redirectUris", array("https://client.example.org/callback")),
                        field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                        field("scopes", array("openid"))));
    }
}
