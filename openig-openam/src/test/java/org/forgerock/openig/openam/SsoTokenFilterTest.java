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
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.BAD_REQUEST;
import static org.forgerock.http.protocol.Status.FORBIDDEN;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.openam.SsoTokenFilter.SSO_TOKEN_KEY;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SsoTokenFilterTest {

    static final private URI OPENAM_URI = URI.create("http://www.example.com:8090/openam/");
    static final private String VALID_TOKEN = "AAAwns...*";
    static final private String REVOKED_TOKEN = "BBBwns...*";
    static final private Object AUTHENTICATION_SUCCEEDED = object(field("tokenId", VALID_TOKEN),
                                                                  field("successUrl", "/openam/console"));
    private static final String DEFAULT_HEADER_NAME = "iPlanetDirectoryPro";

    private SessionContext sessionContext;
    private AttributesContext attributesContext;

    @Mock
    static Handler next;

    @Mock
    static Handler authenticate;

    @Mock
    static Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        sessionContext = new SessionContext(new RootContext(), new SimpleMapSession());
        attributesContext = new AttributesContext(sessionContext);
        attributesContext.getAttributes().put("password", "hifalutin");
    }

    @DataProvider
    private static Object[][] nullRequiredParameters() {
        return new Object[][] {
            { null, OPENAM_URI },
            { next, null } };
    }

    @DataProvider
    private static Object[][] ssoTokenHeaderName() {
        return new Object[][] {
            { null },
            { DEFAULT_HEADER_NAME },
            { "iForgeSession" } };
    }

    @Test(dataProvider = "nullRequiredParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailToCreateFilterWithNullRequiredParameters(final Handler handler,
                                                                   final URI openAmUri) throws Exception {
        new SsoTokenFilter(handler,
                           openAmUri,
                           "/",
                           DEFAULT_HEADER_NAME,
                           Expression.valueOf("bjensen", String.class),
                           Expression.valueOf("${attributes.password}", String.class),
                           logger);
    }

    @Test
    public void shouldCreateRequestForSSOToken() throws Exception  {
        final SsoTokenFilter filter =
                new SsoTokenFilter(authenticate,
                                   OPENAM_URI,
                                   "/myrealm/sub",
                                   DEFAULT_HEADER_NAME,
                                   Expression.valueOf("bjensen", String.class),
                                   Expression.valueOf("${attributes.password}", String.class),
                                   logger);
        final Request request = filter.authenticationRequest(bindings(attributesContext, null));
        assertThat(request.getHeaders().get("X-OpenAM-Username").getFirstValue()).isEqualTo("bjensen");
        assertThat(request.getHeaders().get("X-OpenAM-Password").getFirstValue()).isEqualTo("hifalutin");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUri().toASCIIString()).isEqualTo(OPENAM_URI + "json/myrealm/sub/authenticate");
    }

    @Test(dataProvider = "ssoTokenHeaderName")
    public void shouldPlaceGivenSSOTokenToRequestHeader(final String givenSsoTokenHeaderName) throws Exception {
        // Given
        sessionContext.getSession().put(SSO_TOKEN_KEY, VALID_TOKEN);
        final Request request = new Request();

        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(new Response(OK)));

        // When
        buildSsoTokenFilter(givenSsoTokenHeaderName).filter(attributesContext, request, next);

        // Then
        verify(next).handle(any(Context.class), any(Request.class));
        assertThat(request.getHeaders().get(givenSsoTokenHeaderName != null
                                            ? givenSsoTokenHeaderName
                                            : DEFAULT_HEADER_NAME).getFirstValue()).isEqualTo(VALID_TOKEN);
    }

    @Test
    public void shouldRequestForSSOTokenWhenNoneInSession() throws Exception {
        // Given
        final Request request = new Request();
        final Response responseContainingToken = new Response();
        responseContainingToken.setStatus(OK);
        responseContainingToken.setEntity(AUTHENTICATION_SUCCEEDED);

        when(authenticate.handle(any(Context.class), any(Request.class)))
                                .thenReturn(newResponsePromise(responseContainingToken));

        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(new Response(OK)));

        // When
        buildSsoTokenFilter().filter(attributesContext, request, next);

        // Then
        verify(authenticate).handle(any(Context.class), any(Request.class));
        verify(next).handle(attributesContext, request);
        assertThat(request.getHeaders().get(DEFAULT_HEADER_NAME).getFirstValue()).isEqualTo(VALID_TOKEN);
    }

    @Test
    public void shouldRequestForNewSSOTokenWhenGivenOneIsRevoked() throws Exception {
        // Given token is revoked: first call to the request fails with a forbidden
        // Then, it gets new SSO token and succeed to access to request

        // Given
        sessionContext.getSession().put(SSO_TOKEN_KEY, REVOKED_TOKEN);
        final Request request = new Request();
        final Response forbidden = new Response();
        forbidden.setStatus(FORBIDDEN);

        final Response responseContainingToken = new Response();
        responseContainingToken.setStatus(OK);
        responseContainingToken.setEntity(AUTHENTICATION_SUCCEEDED);

        when(authenticate.handle(any(Context.class), any(Request.class)))
                                .thenReturn(newResponsePromise(responseContainingToken));

        when(next.handle(attributesContext, request)).thenReturn(newResponsePromise(forbidden))
                                            .thenReturn(newResponsePromise(new Response(OK)));

        // When
        final Response finalResponse = buildSsoTokenFilter().filter(attributesContext,
                                                                    request,
                                                                    next).get();

        // Then
        verify(authenticate).handle(any(Context.class), any(Request.class));
        verify(next, times(2)).handle(attributesContext, request);
        assertThat(request.getHeaders().get(DEFAULT_HEADER_NAME).getFirstValue()).isEqualTo(VALID_TOKEN);
        assertThat(finalResponse.getStatus()).isEqualTo(OK);
    }

    @Test
    public void shouldRequestForNewSSOTokenOnlyOnceWhenFirstRequestFailed() throws Exception {
        // Given
        final Request request = new Request();
        final Response forbidden = new Response();
        forbidden.setStatus(FORBIDDEN);

        final Response responseContainingToken = new Response();
        responseContainingToken.setStatus(OK);
        responseContainingToken.setEntity(AUTHENTICATION_SUCCEEDED);

        when(next.handle(any(Context.class), any(Request.class))).thenReturn(newResponsePromise(forbidden));

        when(authenticate.handle(any(Context.class), any(Request.class)))
                                .thenReturn(newResponsePromise(responseContainingToken));

        // When
        final Response finalResponse = buildSsoTokenFilter().filter(attributesContext,
                                                                    request,
                                                                    next).get();

        // Then
        verify(authenticate, times(2)).handle(any(Context.class), any(Request.class));
        verify(next, times(2)).handle(attributesContext, request);
        assertThat(request.getHeaders().containsKey(DEFAULT_HEADER_NAME)).isTrue();
        assertThat(finalResponse).isSameAs(forbidden);
    }


    @Test
    public void shouldRequestForSSOTokenFails() throws Exception {
        // Given
        final Request request = new Request();
        final Response badRequestResponse = new Response();
        badRequestResponse.setStatus(BAD_REQUEST);
        badRequestResponse.setEntity(object(field("OAuth2Error", "An error occurred")));

        when(authenticate.handle(any(Context.class), any(Request.class)))
                .thenReturn(newResponsePromise(badRequestResponse));

        final SsoTokenFilter ssoTokenFilter = buildSsoTokenFilter();

        // When
        final Response finalResponse = ssoTokenFilter.filter(attributesContext, request, next).get();

        // Then
        verifyZeroInteractions(next);
        verify(authenticate).handle(any(Context.class), any(Request.class));
        assertThat(request.getHeaders().containsKey(DEFAULT_HEADER_NAME)).isFalse();
        assertThat(finalResponse.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(finalResponse.getEntity().getString()).isEmpty();
        verify(logger).error("Unable to retrieve SSO Token");

    }

    private static SsoTokenFilter buildSsoTokenFilter() throws Exception {
        return buildSsoTokenFilter(null);
    }

    private static SsoTokenFilter buildSsoTokenFilter(final String headerName) throws Exception {
        return new SsoTokenFilter(authenticate,
                                  OPENAM_URI,
                                  null,
                                  headerName,
                                  Expression.valueOf("bjensen", String.class),
                                  Expression.valueOf("${attributes.password}", String.class),
                                  logger);
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }
}
