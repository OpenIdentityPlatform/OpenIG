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
 * Copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.endsWith;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
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
    public void testNominalInteraction() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("hifalutin", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersSuccess(INITIAL_CREDENTIALS);

        Context context = newContextChain();
        Request request = newRequest();
        Response response = filter.filter(context, request, terminalHandler).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        // Verify that the outgoing message has no authenticate header
        assertThat(response.getHeaders().get(AUTHENTICATE_HEADER)).isNull();
    }

    /**
     * If there is no credentials provided, the filter should not try to forward the request
     */
    @Test
    public void testNoCredentialsProvided() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("${null}", String.class),
                                                             Expression.valueOf("${null}", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        // Always answer with 401
        doAnswer(new UnauthorizedAnswer())
                .when(terminalHandler).handle(any(Context.class), any(Request.class));

        Context context = newContextChain();
        Request request = newRequest();
        filter.filter(context, request, terminalHandler);

        verify(terminalHandler, never()).handle(context, request);
        verify(failureHandler).handle(context, request);
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
                .when(terminalHandler).handle(any(Context.class), any(Request.class));

        Context context = newContextChain();
        Request request = newRequest();
        filter.filter(context, request, terminalHandler);

        // if credentials were rejected all the times, the failure Handler is invoked
        verify(terminalHandler).handle(any(Context.class), any(Request.class));
        verify(failureHandler).handle(context, request);
    }

    /**
     * 2 consecutive requests are sharing the same session.
     * The first one should build and cache the Authorization header.
     * The second should simply re-use the cached value
     * @throws Exception
     */
    @Test
    public void testAuthorizationHeaderCaching() throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                                             Expression.valueOf("hifalutin", String.class),
                                                             failureHandler);
        filter.setCacheHeader(true);

        // No value cached for the first call
        // Subsequent invocations get the cached value
        when(session.get(endsWith(":userpass")))
                .thenReturn(null, INITIAL_CREDENTIALS);

        basicAuthServerAnswersSuccess(INITIAL_CREDENTIALS);

        Context first = newContextChain();
        Request firstRequest = newRequest();
        Response firstResponse = filter.filter(first, firstRequest, terminalHandler).getOrThrow();

        Context second = newContextChain();
        Request secondRequest = newRequest();
        Response secondResponse = filter.filter(second, secondRequest, terminalHandler).getOrThrow();

        // Terminal handler should be called 2 times, not 3
        verify(terminalHandler, times(2)).handle(any(Context.class), any(Request.class));
        // Session should be updated with cached value
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(firstResponse.getStatus()).isEqualTo(Status.OK);
        assertThat(secondResponse.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void testRefreshAuthenticationHeader() throws Exception {

        HttpBasicAuthFilter filter =
                new HttpBasicAuthFilter(Expression.valueOf("bjensen", String.class),
                                        Expression.valueOf("${attributes.password}", String.class),
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
        doAnswer(new AuthorizedAnswer(INITIAL_CREDENTIALS))
                .doAnswer(new AuthorizedAnswer(INITIAL_CREDENTIALS))
                .doAnswer(new UnauthorizedAnswer())
                .doAnswer(new AuthorizedAnswer(REFRESHED_CREDENTIALS))
                .when(terminalHandler).handle(any(Context.class), any(Request.class));

        // Initial round-trip
        Context first = newContextChain();
        AttributesContext firstAttributesContext = first.asContext(AttributesContext.class);
        firstAttributesContext.getAttributes().put("password", "hifalutin");
        Response firstResponse = filter.filter(first, newRequest(), terminalHandler).getOrThrow();

        // Usage of cached value
        Context second = newContextChain();
        Response secondResponse = filter.filter(second, newRequest(), terminalHandler).getOrThrow();

        // Cached value is no longer valid, trigger a user/pass refresh
        Context third = newContextChain();
        AttributesContext thirdAttributesContext = third.asContext(AttributesContext.class);
        thirdAttributesContext.getAttributes().put("password", "hifalutin2");
        Response thirdResponse = filter.filter(third, newRequest(), terminalHandler).getOrThrow();

        // Terminal handler should be called 4 times, not 5
        // first: 2 times
        // second: 1 time
        // third: 2 times
        verify(terminalHandler, times(4)).handle(any(Context.class), any(Request.class));
        // Session should be updated with cached value 2 times
        verify(session).put(endsWith(":userpass"), eq(INITIAL_CREDENTIALS));
        verify(session).put(endsWith(":userpass"), eq(REFRESHED_CREDENTIALS));

        // Responses should be OK for all outgoing responses
        assertThat(firstResponse.getStatus()).isEqualTo(Status.OK);
        assertThat(secondResponse.getStatus()).isEqualTo(Status.OK);
        assertThat(thirdResponse.getStatus()).isEqualTo(Status.OK);
    }

    @Test(dataProvider = "invalidUserNames")
    public void testConformanceErrorIsProducedWhenUsernameContainsColon(final String username) throws Exception {
        HttpBasicAuthFilter filter = new HttpBasicAuthFilter(Expression.valueOf(username, String.class),
                                                             Expression.valueOf("dont-care", String.class),
                                                             failureHandler);
        filter.setCacheHeader(false);

        basicAuthServerAnswersUnauthorizedThenSuccess(INITIAL_CREDENTIALS);

        Response response = filter.filter(newContextChain(), newRequest(), terminalHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).isEmpty();
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
                .when(terminalHandler).handle(any(Context.class), any(Request.class));
    }

    private void basicAuthServerAnswersSuccess(final String credentials) throws Exception {
        doAnswer(new AuthorizedAnswer(credentials))
                .when(terminalHandler).handle(any(Context.class), any(Request.class));
    }

    private Context newContextChain() throws Exception {
        return new AttributesContext(new SessionContext(new RootContext(), session));
    }

    private Request newRequest() throws Exception {
        Request request = new Request();
        request.setUri("http://openig.forgerock.org");
        return request;
    }

    private static class UnauthorizedAnswer implements Answer<Promise<Response, NeverThrowsException>> {
        // 1st time called: Mock a 401 (Unauthorized status) response
        @Override
        public Promise<Response, NeverThrowsException> answer(InvocationOnMock invocation) throws Throwable {
            Response response = new Response();
            response.setStatus(Status.UNAUTHORIZED);
            response.getHeaders().put(AUTHENTICATE_HEADER, "Basic realm=\"Login\"");
            return Promises.newResultPromise(response);
        }
    }

    private static class AuthorizedAnswer implements Answer<Promise<Response, NeverThrowsException>> {

        private final String credentials;

        public AuthorizedAnswer(final String credentials) {
            this.credentials = credentials;
        }

        @Override
        public Promise<Response, NeverThrowsException> answer(InvocationOnMock invocation) throws Throwable {
            Request request = (Request) invocation.getArguments()[1];

            // Verify the authorization header: base64(user:pass)
            assertThat(request.getHeaders().getFirst(AUTHORIZATION_HEADER))
                    .isEqualTo("Basic " + credentials);

            // Produce a valid response, no special header is required
            Response response = new Response();
            response.setStatus(Status.OK);
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
