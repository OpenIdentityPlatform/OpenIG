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
import static org.forgerock.http.protocol.Status.CREATED;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.filter.oauth2.client.HeapUtilsTest.buildDefaultHeap;
import static org.forgerock.openig.filter.oauth2.client.Issuer.ISSUER_KEY;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
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
    private static final String SUFFIX = "ForMyApp";

    private AttributesContext attributesContext;
    private Context context;

    @Captor
    private ArgumentCaptor<Request> captor;

    @Mock
    private Handler handler;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        attributesContext = new AttributesContext(new RootContext());
        context = attributesContext;
    }

    @Test
    public void shouldFailToPerformDynamicRegistrationWhenMissingRedirectUris() throws Exception {
        // given
        final JsonValue invalidConfig = json(object(
                                                field("contact", array("ve7jtb@example.org", "bjensen@example.org")),
                                                field("scopes", array("openid"))));

        attributesContext.getAttributes().put(ISSUER_KEY, new Issuer("myIssuer",
                                                                     issuerConfigWithAllRequestedEndpoints()));
        final Heap heap = mock(Heap.class);
        when(heap.get("myIssuer" + SUFFIX, ClientRegistration.class)).thenReturn(null);
        final ClientRegistrationFilter crf = new ClientRegistrationFilter(handler, invalidConfig, heap, SUFFIX);

        // when
        final Response response = crf.filter(context, new Request(), handler).get();

        // then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).contains("'redirect_uris' should be defined");
    }

    @Test
    public void shouldFailToPerformDynamicRegistrationWhenIssuerHasNoRegistrationEndpoint() throws Exception {
        // given
        attributesContext.getAttributes().put(ISSUER_KEY, new Issuer("myIssuer",
                                                                     issuerConfigWithNoRegistrationEndpoint()));
        final Heap heap = mock(Heap.class);
        when(heap.get("myIssuer" + SUFFIX, ClientRegistration.class)).thenReturn(null);
        final ClientRegistrationFilter crf = new ClientRegistrationFilter(handler, getFilterConfig(), heap, SUFFIX);

        // when
        final Response response = crf.filter(context, new Request(), handler).get();

        // then
        assertThat(response.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).contains("Registration is not supported by the issuer");
    }

    @Test
    public void shouldPerformDynamicRegistration() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildClientRegistrationFilter();
        final Response response = new Response();
        response.setStatus(CREATED);
        response.setEntity(json(object()));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        drf.performDynamicClientRegistration(context, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));

        // then
        verify(handler).handle(eq(context), captor.capture());
        Request request = captor.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEntity().toString()).containsSequence("redirect_uris", "contact", "scopes");
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailWhenStatusCodeResponseIsDifferentFromCreated() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildClientRegistrationFilter();
        final Response response = new Response();
        response.setStatus(Status.BAD_REQUEST);
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        drf.performDynamicClientRegistration(context, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
    }

    @Test(expectedExceptions = RegistrationException.class)
    public void shouldFailWhenResponseHasInvalidResponseContent() throws Exception {
        // given
        final ClientRegistrationFilter drf = buildClientRegistrationFilter();
        final Response response = new Response();
        response.setStatus(CREATED);
        response.setEntity(array("invalid", "content"));
        when(handler.handle(eq(context), any(Request.class))).thenReturn(newResponsePromise(response));

        // when
        drf.performDynamicClientRegistration(context, getFilterConfig(), new URI(SAMPLE_URI + REGISTRATION_ENDPOINT));
    }

    private ClientRegistrationFilter buildClientRegistrationFilter() throws HeapException, Exception {
        return new ClientRegistrationFilter(handler, getFilterConfig(), buildDefaultHeap(), SUFFIX);
    }

    private JsonValue getFilterConfig() {
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
}
