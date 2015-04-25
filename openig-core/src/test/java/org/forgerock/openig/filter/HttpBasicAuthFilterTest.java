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
 * Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpContext;
import org.forgerock.http.Session;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
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
    public void testHeadersAreRemoved() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(null, null, failureHandler);
        filter.setCacheHeader(false);

        Exchange exchange = newExchange();
        Request request = newRequest();
        request.getHeaders().putSingle(AUTHORIZATION_HEADER, "Basic azerty");

        doAnswer(new Answer<Promise<Response, ResponseException>>() {
            @Override
            public Promise<Response, ResponseException> answer(final InvocationOnMock invocation) throws Throwable {
                // Produce a valid response with an authentication challenge
                Response response = new Response();
                response.setStatus(HTTP_SUCCESS);
                response.getHeaders().putSingle(AUTHENTICATE_HEADER, "Realm toto");
                return Promises.newResultPromise(response);

            }
        }).when(terminalHandler).handle(eq(exchange),
                                        argThat(new AbsenceOfHeaderInRequest(AUTHORIZATION_HEADER)));

        Response response = filter.filter(exchange, request, terminalHandler).getOrThrow();

        // Verify that the outgoing message has no authenticate header
        assertThat(response.getHeaders().get(AUTHENTICATE_HEADER))
                .isNull();
    }

    @Test
    public void testNominalInteraction() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("hifalutin", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        Exchange exchange = newExchange();
        Request request = newRequest();
        Response response = filter.filter(exchange, request, terminalHandler).getOrThrow();

        assertThat(response.getStatus()).isEqualTo(HTTP_SUCCESS);
    }

    /**
     * If there is no credentials provided, the filter should not try to forward the request more than once
     */
    @Test
    public void testNoCredentialsProvided() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("${null}", String.class),
                                                             Expression.valueOf("${null}", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        // Always answer with 401
        doAnswer(new UnauthorizedAnswer())
                .when(terminalHandler).handle(any(Exchange.class), any(Request.class));

        Exchange exchange = newExchange();
        Request request = newRequest();
        filter.filter(exchange, request, terminalHandler);

        verify(terminalHandler, times(1)).handle(exchange, request);
        verify(failureHandler).handle(exchange, request);
    }

    /**
     * If there are credentials but invalid ones (not accepted by the target application), this filter should try
     * 2 times: one with the old credential (or no credential if none are cached), and another one
     * with the refreshed credentials.
     */
    @Test
    public void testInvalidCredentialsProvided() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("hifalutin", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        // Always answer with 401
        doAnswer(new UnauthorizedAnswer())
                .when(terminalHandler).handle(any(Exchange.class), any(Request.class));

        Exchange exchange = newExchange();
        Request request = newRequest();
        filter.filter(exchange, request, terminalHandler);

        // if credentials were rejected all the times, the failure Handler is invoked
        verify(terminalHandler, times(2)).handle(exchange, request);
        verify(failureHandler).handle(exchange, request);
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
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("hifalutin", String.class),
                                                             failureHandler);
        filter.setCacheHeader(true);

        // No value cached for the first call
        // Subsequent invocations get the cached value
        when(session.get(endsWith(":userpass")))
                .thenReturn(null, INITIAL_CREDENTIALS);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        Exchange first = newExchange();
        Request firstRequest = newRequest();
        Response firstResponse = filter.filter(first, firstRequest, terminalHandler).getOrThrow();

        Exchange second = newExchange();
        Request secondRequest = newRequest();
        Response secondResponse = filter.filter(second, secondRequest, terminalHandler).getOrThrow();

        // Terminal handler should be called 3 times, not 4
        verify(terminalHandler, times(3)).handle(any(Exchange.class), any(Request.class));
        // Session should be updated with cached value
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(firstResponse.getStatus()).isEqualTo(HTTP_SUCCESS);
        assertThat(secondResponse.getStatus()).isEqualTo(HTTP_SUCCESS);
    }

    @Test
    public void testRefreshAuthenticationHeader() throws Exception {

        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("${exchange.password}", String.class),
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
                .when(terminalHandler).handle(any(Exchange.class), any(Request.class));

        // Initial round-trip
        Exchange first = newExchange();
        first.put("password", "hifalutin");
        Response firstResponse = filter.filter(first, newRequest(), terminalHandler).getOrThrow();

        // Usage of cached value
        Exchange second = newExchange();
        Response secondResponse = filter.filter(second, newRequest(), terminalHandler).getOrThrow();

        // Cached value is no longer valid, trigger a user/pass refresh
        Exchange third = newExchange();
        third.put("password", "hifalutin2");
        Response thirdResponse = filter.filter(third, newRequest(), terminalHandler).getOrThrow();

        // Terminal handler should be called 5 times, not 6
        // first: 2 times
        // second: 1 time
        // third: 2 times
        verify(terminalHandler, times(5)).handle(any(Exchange.class), any(Request.class));
        // Session should be updated with cached value 2 times
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));
        verify(session).put(endsWith(":userpass"), eq(REFRESHED_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(firstResponse.getStatus()).isEqualTo(HTTP_SUCCESS);
        assertThat(secondResponse.getStatus()).isEqualTo(HTTP_SUCCESS);
        assertThat(thirdResponse.getStatus()).isEqualTo(HTTP_SUCCESS);
    }

    @Test(dataProvider = "invalidUserNames",
          expectedExceptions = ResponseException.class,
          expectedExceptionsMessageRegExp = "username must not contain a colon ':' character")
    public void testConformanceErrorIsProducedWhenUsernameContainsColon(final String username) throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf(username, String.class),
                                                             Expression.valueOf("dont-care", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        filter.filter(newExchange(), newRequest(), terminalHandler).getOrThrow();
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
                .when(terminalHandler).handle(any(Exchange.class), any(Request.class));
    }

    private Exchange newExchange() throws Exception {
        Exchange exchange = new Exchange();
        exchange.parent = new HttpContext(null, session);
        return exchange;
    }

    private Request newRequest() throws Exception {
        Request request = new Request();
        request.setUri("http://openig.forgerock.org");
        return request;
    }

    private static class UnauthorizedAnswer implements Answer<Promise<Response, ResponseException>> {
        // 1st time called: Mock a 401 (Unauthorized status) response
        @Override
        public Promise<Response, ResponseException> answer(InvocationOnMock invocation) throws Throwable {
            Response response = new Response();
            response.setStatus(HTTP_UNAUTHORIZED);
            response.getHeaders().putSingle(AUTHENTICATE_HEADER, "Basic realm=\"Login\"");
            return Promises.newResultPromise(response);
        }
    }

    private static class AuthorizedAnswer implements Answer<Promise<Response, ResponseException>> {

        private final String credentials;

        public AuthorizedAnswer(final String credentials) {
            this.credentials = credentials;
        }

        @Override
        public Promise<Response, ResponseException> answer(InvocationOnMock invocation) throws Throwable {
            Request request = (Request) invocation.getArguments()[1];

            // Verify the authorization header: base64(user:pass)
            assertThat(request.getHeaders().getFirst(AUTHORIZATION_HEADER))
                    .isEqualTo("Basic " + credentials);

            // Produce a valid response, no special headers are required
            Response response = new Response();
            response.setStatus(HTTP_SUCCESS);
            return Promises.newResultPromise(response);
        }
    }

    private class AbsenceOfHeaderInRequest extends BaseMatcher<Request> {

        private final String headerName;

        private AbsenceOfHeaderInRequest(final String headerName) {
            this.headerName = headerName;
        }

        @Override
        public boolean matches(final Object o) {
            if (!(o instanceof Request)) {
                return false;
            }

            Request request = (Request) o;
            return request.getHeaders().get(headerName) == null;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("headers[" + headerName + "] is not null");
        }
    }
}
