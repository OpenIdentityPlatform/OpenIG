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

package org.forgerock.openig.openam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TokenTransformationFilterTest {

    private static final String SAML_ASSERTIONS = "[The result SAML assertions]";
    private static final String ID_TOKEN_JWT = "[Some OpenID Connect id_token JWT]";
    private static final Response ISSUED_TOKEN_RESPONSE =
            new Response(Status.OK).setEntity(object(field("issued_token", SAML_ASSERTIONS)));
    private static final Response ERROR_RESPONSE =
            new Response(Status.UNAUTHORIZED).setEntity(object(field("reason", "token_validation"),
                                                               field("message", "Blah blah ...")));
    private static final Response SSO_TOKEN_RESPONSE =
            new Response(Status.OK).setEntity(object(field("tokenId", "abcdefg"),
                                                     field("successUrl", "/openam/console")));


    @Mock
    private Handler transformationHandler;

    @Mock
    private Handler next;

    @Captor
    private ArgumentCaptor<Request> captor;

    private AttributesContext attributesContext;
    private Context context;

    private HeapImpl heap;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        attributesContext = new AttributesContext(new RootContext());
        context = attributesContext;
        attributesContext.getAttributes().put("id_token", ID_TOKEN_JWT);
        heap = new HeapImpl(Name.of("heap"));
        heap.put(Keys.TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put("#mock-handler", transformationHandler);
    }

    @DataProvider
    public static Object[][] uriBuildingBlocks() {
        // @Checkstyle:off
        return new Object[][] {
                // baseUri, realm, instance -> expected
                { "http://openam.example.com/openam/", null,          "instance",  "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/",           "instance",  "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/realm",      "instance",  "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "realm",       "instance",  "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/sub/realm",  "instance",  "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/sub/realm/", "instance",  "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", null,          "/instance", "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/",           "/instance", "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/realm",      "/instance", "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "realm",       "/instance", "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/sub/realm",  "/instance", "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam/", "/sub/realm/", "/instance", "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  null,          "instance",  "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/",           "instance",  "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/realm",      "instance",  "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "realm",       "instance",  "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/sub/realm",  "instance",  "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/sub/realm/", "instance",  "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  null,          "/instance", "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/",           "/instance", "http://openam.example.com/openam/rest-sts/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/realm",      "/instance", "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "realm",       "/instance", "http://openam.example.com/openam/rest-sts/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/sub/realm",  "/instance", "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
                { "http://openam.example.com/openam",  "/sub/realm/", "/instance", "http://openam.example.com/openam/rest-sts/sub/realm/instance?_action=translate" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "uriBuildingBlocks")
    public void shouldCreateTranslationUrlWithRealm(String baseUri, String realm, String instance, String expected)
            throws Exception {
        when(transformationHandler.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(SSO_TOKEN_RESPONSE));

        JsonValue config = json(object(field("openamUri", baseUri),
                                       field("realm", realm),
                                       field("username", "guillaume"),
                                       field("password", "s3cr3t"),
                                       field("idToken", "${attributes.id_token}"),
                                       field("target", "${attributes.saml}"),
                                       field("instance", instance),
                                       field("amHandler", "#mock-handler")));
        Filter filter = (Filter) new TokenTransformationFilter.Heaplet().create(Name.of("this"), config, heap);
        filter.filter(new SessionContext(context, mock(Session.class)), new Request(), next);

        verify(transformationHandler, times(2)).handle(any(Context.class), captor.capture());
        Request transformationRequest = captor.getAllValues().get(1);
        assertThat(transformationRequest.getUri().asURI())
                .isEqualTo(new URI(expected));
    }

    @Test
    public void shouldTransformIdTokenToSamlAssertions() throws Exception {
        when(transformationHandler.handle(eq(context), any(Request.class)))
                .thenReturn(newResponsePromise(ISSUED_TOKEN_RESPONSE));

        TokenTransformationFilter filter =
                new TokenTransformationFilter(transformationHandler,
                                              new URI("http://openam.example.com/"),
                                              Expression.valueOf("${attributes.id_token}",
                                                                 String.class),
                                              Expression.valueOf("${attributes.saml_token}",
                                                                 String.class));

        Request request = new Request();
        filter.filter(context, request, next);

        verify(transformationHandler).handle(eq(context), captor.capture());
        Request transformationRequest = captor.getValue();
        JsonValue transformation = new JsonValue(transformationRequest.getEntity().getJson());
        assertThat(transformation.get(new JsonPointer("input_token_state/oidc_id_token")).asString())
                .isEqualTo(ID_TOKEN_JWT);
        assertThat(attributesContext.getAttributes()).contains(entry("saml_token", SAML_ASSERTIONS));

        // Original request has to be forwarded
        verify(next).handle(context, request);
    }

    @Test
    public void shouldFailWhenNoTransformedTokenIsIssued() throws Exception {
        when(transformationHandler.handle(eq(context), any(Request.class)))
                .thenReturn(newResponsePromise(ERROR_RESPONSE));

        TokenTransformationFilter filter =
                new TokenTransformationFilter(transformationHandler,
                                              new URI("http://openam.example.com/"),
                                              Expression.valueOf("${attributes.id_token}",
                                                                 String.class),
                                              Expression.valueOf("${attributes.saml_token}",
                                                                 String.class));

        Request request = new Request();
        Response response = filter.filter(context, request, next).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);

        // Original request has not been forwarded
        verifyZeroInteractions(next);
    }
}
