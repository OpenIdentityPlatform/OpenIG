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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class HttpBasicAuthFilterTest {

    public static final String AUTHENTICATE_HEADER = "WWW-Authenticate";
    public static final int HTTP_SUCCESS = 200;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final String INITIAL_CREDENTIALS = "YmplbnNlbjpoaWZhbHV0aW4=";
    public static final String REFRESHED_CREDENTIALS = "YmplbnNlbjpoaWZhbHV0aW4y";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Mock
    private Handler terminalHandler;

    @Mock
    private Handler failureHandler;

    @Mock
    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExpressionEvaluation() throws Exception {
        // TODO Move this test out of here, it has nothing to do with testing the behavior of this filter
        Expression username = new Expression("realm\\${exchange.request.headers['username'][0]}");
        Expression password = new Expression("${exchange.request.headers['password'][0]}");
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.method = "GET";
        exchange.request.uri = new URI("http://test.com:123/path/to/resource.html");
        exchange.request.headers.add("username", "Myname");
        exchange.request.headers.add("password", "Mypass");

        String user = username.eval(exchange, String.class);
        String pass = password.eval(exchange, String.class);

        assertThat(user).isEqualTo("realm\\Myname");
        assertThat(pass).isEqualTo("Mypass");
    }

    @Test
    public void testHeadersAreRemoved() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(null, null, failureHandler);
        filter.setCacheHeader(false);

        Exchange exchange = newExchange();
        exchange.request.headers.putSingle(AUTHORIZATION_HEADER, "Basic azerty");

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                Exchange exchange = (Exchange) invocation.getArguments()[0];

                // Produce a valid response with an authentication challenge
                exchange.response = new Response();
                exchange.response.status = HTTP_SUCCESS;
                exchange.response.headers.putSingle(AUTHENTICATE_HEADER, "Realm toto");
                return null;

            }
        }).when(terminalHandler).handle(exchange);

        filter.filter(exchange, terminalHandler);

        // Verify that the terminal handler has been called with an exchange that does
        // not have the original authorization header
        verify(terminalHandler).handle(argThat(new AbsenceOfHeaderInRequest(AUTHORIZATION_HEADER)));
        // Verify that the outgoing message has no authenticate header
        assertThat(exchange.response.headers.get(AUTHENTICATE_HEADER))
                .isNull();
    }

    @Test
    public void testNominalInteraction() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression("bjensen"),
                                                             new Expression("hifalutin"),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        Exchange exchange = newExchange();
        filter.filter(exchange, terminalHandler);

        assertThat(exchange.response.status).isEqualTo(HTTP_SUCCESS);
    }

    /**
     * If there is no credentials provided, the filter should not try to forward the request more than once
     */
    @Test
    public void testNoCredentialsProvided() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression("${null}"),
                                                             new Expression("${null}"),
                                                             failureHandler);
        filter.setCacheHeader(false);

        // Always answer with 401
        doAnswer(new UnauthorizedAnswer())
                .when(terminalHandler).handle(any(Exchange.class));

        Exchange exchange = newExchange();
        filter.filter(exchange, terminalHandler);

        verify(terminalHandler, times(1)).handle(exchange);
        verify(failureHandler).handle(exchange);
    }

    /**
     * If there are credentials but invalid ones (not accepted by the target application), this filter should try
     * 2 times: one with the old credential (or no credential if none are cached), and another one
     * with the refreshed credentials.
     */
    @Test
    public void testInvalidCredentialsProvided() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression("bjensen"),
                                                             new Expression("hifalutin"),
                                                             failureHandler);
        filter.setCacheHeader(false);

        // Always answer with 401
        doAnswer(new UnauthorizedAnswer())
                .when(terminalHandler).handle(any(Exchange.class));

        Exchange exchange = newExchange();
        filter.filter(exchange, terminalHandler);

        // if credentials were rejected all the times, the failure Handler is invoked
        verify(terminalHandler, times(2)).handle(exchange);
        verify(failureHandler).handle(exchange);
    }

    /**
     * 2 consecutive requests are sharing the same session.
     * The first one should build and cache the Authorization header after a round trip
     * to the next handler (firstly answer with a challenge).
     * The second should simply re-use the cached value
     * @throws Exception
     */
    @Test
    public void tesAuthorizationHeaderCaching() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression("bjensen"),
                                                             new Expression("hifalutin"),
                                                             failureHandler);
        filter.setCacheHeader(true);

        // No value cached for the first call
        // Subsequent invocations get the cached value
        when(session.get(endsWith(":userpass")))
                .thenReturn(null, INITIAL_CREDENTIALS);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        Exchange first = newExchange();
        filter.filter(first, terminalHandler);

        Exchange second = newExchange();
        filter.filter(second, terminalHandler);

        // Terminal handler should be called 3 times, not 4
        verify(terminalHandler, times(3)).handle(any(Exchange.class));
        // Session should be updated with cached value
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(first.response.status).isEqualTo(HTTP_SUCCESS);
        assertThat(second.response.status).isEqualTo(HTTP_SUCCESS);
    }

    @Test
    public void testRefreshAuthenticationHeader() throws Exception {

        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression("bjensen"),
                                                             new Expression("${exchange.password}"),
                                                             failureHandler);
        filter.setCacheHeader(true);

        // Mock cache content for credentials
        when(session.get(endsWith(":userpass")))
                .thenReturn(
                    null,
                    INITIAL_CREDENTIALS,
                    INITIAL_CREDENTIALS,
                    REFRESHED_CREDENTIALS);

        // Scenario:
        //  first request (cache the value after initial round-trip)
        //  second request (cached value is OK)
        //  third request (cached value is no longer valid, trigger a refresh)
        doAnswer(new UnauthorizedAnswer())
                .doAnswer(new AuthorizedAnswer(INITIAL_CREDENTIALS))
                .doAnswer(new AuthorizedAnswer(INITIAL_CREDENTIALS))
                .doAnswer(new UnauthorizedAnswer())
                .doAnswer(new AuthorizedAnswer(REFRESHED_CREDENTIALS))
                .when(terminalHandler).handle(any(Exchange.class));

        // Initial round-trip
        Exchange first = newExchange();
        first.put("password", "hifalutin");
        filter.filter(first, terminalHandler);

        // Usage of cached value
        Exchange second = newExchange();
        filter.filter(second, terminalHandler);

        // Cached value is no longer valid, trigger a user/pass refresh
        Exchange third = newExchange();
        third.put("password", "hifalutin2");
        filter.filter(third, terminalHandler);

        // Terminal handler should be called 5 times, not 6
        // first: 2 times
        // second: 1 time
        // third: 2 times
        verify(terminalHandler, times(5)).handle(any(Exchange.class));
        // Session should be updated with cached value 2 times
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));
        verify(session).put(endsWith(":userpass"), eq(REFRESHED_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(first.response.status).isEqualTo(HTTP_SUCCESS);
        assertThat(second.response.status).isEqualTo(HTTP_SUCCESS);
        assertThat(third.response.status).isEqualTo(HTTP_SUCCESS);
    }

    @Test(dataProvider = "invalidUserNames",
          expectedExceptions = HandlerException.class,
          expectedExceptionsMessageRegExp = "username must not contain a colon ':' character")
    public void testConformanceErrorIsProducedWhenUsernameContainsColon(final String username) throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(new Expression(username),
                                                             new Expression("dont-care"),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        filter.filter(newExchange(), terminalHandler);
    }

    @DataProvider
    public static Object[][] invalidUserNames() {
        return new Object[][] {
            { ":starting-with-colon" },
            { "colon-:-in-the-middle" },
            { "ending-with-colon:" } };
    }

    private void basicAuthServerAnswersUnauthorizedThenSuccess(final String credentials) throws Exception {
        doAnswer(new UnauthorizedAnswer())
                .doAnswer(new AuthorizedAnswer(credentials))
                .when(terminalHandler).handle(any(Exchange.class));
    }

    private Exchange newExchange() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://openig.forgerock.org");
        exchange.session = session;
        return exchange;
    }

    private static class UnauthorizedAnswer implements Answer<Void> {
        // 1st time called: Mock a 401 (Unauthorized status) response
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            Exchange exchange = (Exchange) invocation.getArguments()[0];
            exchange.response = new Response();
            exchange.response.status = HTTP_UNAUTHORIZED;
            exchange.response.headers.putSingle(AUTHENTICATE_HEADER, "Basic realm=\"Login\"");
            return null;
        }
    }

    private static class AuthorizedAnswer implements Answer<Void> {

        private final String credentials;

        public AuthorizedAnswer(final String credentials) {
            this.credentials = credentials;
        }

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            Exchange exchange = (Exchange) invocation.getArguments()[0];

            // Verify the authorization header: base64(user:pass)
            assertThat(exchange.request.headers.getFirst(AUTHORIZATION_HEADER))
                    .isEqualTo("Basic " + credentials);

            // Produce a valid response, no special headers are required
            exchange.response = new Response();
            exchange.response.status = HTTP_SUCCESS;
            return null;
        }
    }

    private class AbsenceOfHeaderInRequest extends BaseMatcher<Exchange> {

        private final String headerName;

        private AbsenceOfHeaderInRequest(final String headerName) {
            this.headerName = headerName;
        }

        @Override
        public boolean matches(final Object o) {
            if (!(o instanceof Exchange)) {
                return false;
            }

            Exchange exchange = (Exchange) o;
            return exchange.request.headers.get(headerName) == null;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("headers[" + headerName + "] is not null");
        }
    }
}
