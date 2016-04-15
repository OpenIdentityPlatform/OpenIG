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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.authz.modules.oauth2;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.forgerock.authz.modules.oauth2.ResourceServerFilter.WWW_AUTHENTICATE_HEADER;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.Condition;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.TimeService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ResourceServerFilterTest {

    /**
     * Extract scopes value.
     */
    private static final Pattern SCOPES_VALUE = Pattern.compile(".*scope=\"([^\"]*)\".*");

    /**
     * Re-used token-id.
     */
    public static final String TOKEN_ID = "1fc0e143-f248-4e50-9c13-1d710360cec9";

    private static final String TEST_REALM = "Example";

    @Mock
    private Handler nextHandler;

    @Mock
    private AccessTokenResolver resolver;

    @Mock
    private AccessTokenInfo token;

    @Mock
    private TimeService time;

    @Mock
    private Logger logger;

    @Mock
    private ResourceAccess resourceAccess;

    @Captor
    private ArgumentCaptor<OAuth2Context> capture;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(resolver.resolve(any(Context.class), eq(TOKEN_ID)))
                .thenReturn(Promises.<AccessTokenInfo, AccessTokenException>newResultPromise(token));
        when(token.getScopes())
                .thenReturn(new HashSet<>(asList("a", "b", "c")));

        // By default consider all token as valid since their expiration time is greater than now
        when(token.getExpiresAt()).thenReturn(100L);
        when(time.now()).thenReturn(0L);
    }

    @DataProvider
    public static Object[][] unauthorizedHeaderValues() {
        // @Checkstyle:off
        return new Object[][] {
                { null }, // no header
                { "" }, // empty header
                { "NoABearerToken value" } // no 'Bearer' token
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "unauthorizedHeaderValues")
    public void shouldFailWithUnauthorizedGenericError(final String authorizationValue) throws Exception {
        ResourceServerFilter filter = buildResourceServerFilter();

        Request request = buildUnAuthorizedRequest();
        if (authorizationValue != null) {
            request.getHeaders().put("Authorization", authorizationValue);
        }
        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE_HEADER))
                .isEqualTo(doubleQuote("Bearer realm='" + TEST_REALM + "'"));
    }

    @Test
    public void shouldFailBecauseOfMultipleAuthorizationHeaders() throws Exception {
        ResourceServerFilter filter = buildResourceServerFilter();

        Request request = buildUnAuthorizedRequest();
        request.getHeaders().add("Authorization", "Bearer 1234");
        request.getHeaders().add("Authorization", "Bearer 5678");

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE_HEADER))
                .startsWith(doubleQuote("Bearer realm='" + TEST_REALM + "', error='invalid_request'"));
    }

    @Test
    public void shouldFailBecauseOfUnresolvableToken() throws Exception {
        when(resolver.resolve(any(Context.class), eq(TOKEN_ID)))
                .thenReturn(Promises.<AccessTokenInfo, AccessTokenException> newExceptionPromise(
                                                                                   new AccessTokenException("error")));
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    @Test
    public void shouldFailBecauseOfExpiredToken() throws Exception {
        // Compared to the expiration date (100L), now is greater, so the token is expired
        when(time.now()).thenReturn(2000L);
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    private void runAndExpectUnauthorizedInvalidTokenResponse() throws Exception {
        ResourceServerFilter filter = buildResourceServerFilter();
        Request request = buildAuthorizedRequest();

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE_HEADER))
                .startsWith(doubleQuote("Bearer realm='" + TEST_REALM + "', error='invalid_token'"));
    }

    @Test
    public void shouldFailBecauseOfMissingScopes() throws Exception {
        ResourceServerFilter filter = buildResourceServerFilter("a-missing-scope", "another-one");
        Request request = buildAuthorizedRequest();

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.FORBIDDEN);
        String header = response.getHeaders().getFirst(WWW_AUTHENTICATE_HEADER);
        assertThat(header).has(scopes("a-missing-scope", "another-one"));
    }

    @Test
    public void shouldFailDueToResourceAccessException() throws Exception {
        final ResponseException responseException = new ResponseException("boom");
        when(resourceAccess.getRequiredScopes(any(Context.class), any(Request.class)))
                           .thenThrow(responseException);
        final ResourceServerFilter filter = newResourceServerFilter();

        Request request = buildAuthorizedRequest();
        Response response = filter.filter(newContextChain(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getCause()).isSameAs(responseException);
    }

    @Test
    public void shouldEvaluateScopesAndStoreTokenInContext() throws Exception {
        final ResourceServerFilter filter = buildResourceServerFilter();

        Context context = newContextChain();
        Request request = buildAuthorizedRequest();
        filter.filter(context, request, nextHandler);

        verify(resourceAccess).getRequiredScopes(context, request);
        verify(nextHandler).handle(capture.capture(), eq(request));
        assertThat(capture.getValue().getParent()).isEqualTo(context);
        assertThat(capture.getValue().getAccessToken()).isEqualTo(token);
    }

    private ResourceServerFilter buildResourceServerFilter(String... scopes) throws ResponseException {
        when(resourceAccess.getRequiredScopes(any(Context.class), any(Request.class)))
                           .thenReturn(new HashSet<>(Arrays.asList(scopes)));
        return newResourceServerFilter();
    }

    private ResourceServerFilter newResourceServerFilter() {
        return new ResourceServerFilter(resolver, time, resourceAccess, TEST_REALM);
    }

    private static Context newContextChain() {
        return new AttributesContext(new RootContext());
    }

    private static Request buildUnAuthorizedRequest() {
        return new Request();
    }

    private static Request buildAuthorizedRequest() {
        Request request = new Request();
        request.getHeaders().add("Authorization", format("Bearer %s", TOKEN_ID));
        return request;
    }

    private static String doubleQuote(final String value) {
        return value.replaceAll("'", "\"");
    }

    /**
     * Protect against Java 7/8 collection changes about ordering.
     * Check that the value contains {@code scope="[a list of single-space-separated scopes]"}
     */
    private static Condition<? super String> scopes(final String... values) {
        return new Condition<String>(format("scope='%s' (in any order)", asList(values))) {

            private final Set<String> scopes = newLinkedHashSet(values);

            @Override
            public boolean matches(String value) {
                Matcher matcher = SCOPES_VALUE.matcher(value);
                if (!matcher.matches()) {
                    return false;
                }
                String matched = matcher.group(1);

                // Can't rely on ordering so I just fail if the matched value doesn't contains the required scope
                int size = 0;
                for (String scope : scopes) {
                    if (!matched.contains(scope)) {
                        return false;
                    }
                    size += scope.length();
                }

                // A simple check if there are additional scopes:
                // Verify string length (each scope size + number of scopes minus 1 for the space separators)
                size += scopes.size() - 1;
                return matched.length() == size;
            }
        };
    }
}
