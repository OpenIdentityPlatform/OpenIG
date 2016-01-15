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

package org.forgerock.openig.filter.oauth2;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;
import static org.forgerock.openig.filter.oauth2.OAuth2ResourceServerFilter.DEFAULT_ACCESS_TOKEN_KEY;
import static org.forgerock.openig.filter.oauth2.OAuth2ResourceServerFilter.DEFAULT_REALM_NAME;
import static org.forgerock.openig.filter.oauth2.challenge.AuthenticateChallengeHandler.WWW_AUTHENTICATE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.Condition;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2ResourceServerFilterTest {

    /**
     * Extract scopes value.
     */
    private static final Pattern SCOPES_VALUE = Pattern.compile(".*scope=\"(.*)\".*");

    /**
     * Re-used token-id.
     */
    public static final String TOKEN_ID = "1fc0e143-f248-4e50-9c13-1d710360cec9";

    @Mock
    private Handler nextHandler;

    @Mock
    private AccessTokenResolver resolver;

    @Mock
    private AccessToken token;

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(resolver.resolve(any(Context.class), eq(TOKEN_ID)))
                .thenReturn(Promises.<AccessToken, OAuth2TokenException>newResultPromise(token));
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
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        Request request = buildUnAuthorizedRequest();
        if (authorizationValue != null) {
            request.getHeaders().put("Authorization", authorizationValue);
        }
        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .isEqualTo(doubleQuote("Bearer realm='OpenIG'"));
    }

    @Test
    public void shouldFailBecauseOfMultipleAuthorizationHeaders() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        Request request = buildUnAuthorizedRequest();
        request.getHeaders().add("Authorization", "Bearer 1234");
        request.getHeaders().add("Authorization", "Bearer 5678");

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .startsWith(doubleQuote("Bearer realm='OpenIG', error='invalid_request'"));
    }

    @Test
    public void shouldFailBecauseOfUnresolvableToken() throws Exception {
        when(resolver.resolve(any(Context.class), eq(TOKEN_ID)))
                .thenReturn(oAuth2TokenExceptionPromise());
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    private Promise<AccessToken, OAuth2TokenException> oAuth2TokenExceptionPromise() {
        return Promises.<AccessToken, OAuth2TokenException>newExceptionPromise(new OAuth2TokenException("error"));
    }

    @Test
    public void shouldFailBecauseOfExpiredToken() throws Exception {
        // Compared to the expiration date (100L), now is greater, so the token is expired
        when(time.now()).thenReturn(2000L);
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    private void runAndExpectUnauthorizedInvalidTokenResponse() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();
        Request request = buildAuthorizedRequest();

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .startsWith(doubleQuote("Bearer realm='OpenIG', error='invalid_token'"));
    }

    @Test
    public void shouldFailBecauseOfMissingScopes() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter("a-missing-scope", "another-one");
        Request request = buildAuthorizedRequest();

        Response response = filter.filter(newContextChain(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.FORBIDDEN);
        String header = response.getHeaders().getFirst(WWW_AUTHENTICATE);
        assertThat(header).has(scopes("another-one", "a-missing-scope"));
    }

    @Test
    public void shouldStoreAccessTokenInTheContext() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        Context context = newContextChain();
        Request request = buildAuthorizedRequest();
        filter.filter(context, request, nextHandler);

        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        assertThat(attributesContext.getAttributes()).containsKey(DEFAULT_ACCESS_TOKEN_KEY);
        verify(nextHandler).handle(context, request);
    }

    @Test
    public void shouldStoreAccessTokenInTargetInTheContext() throws Exception {
        final OAuth2ResourceServerFilter filter = new OAuth2ResourceServerFilter(resolver,
                new BearerTokenExtractor(),
                time,
                Expression.valueOf("${attributes.myToken}", String.class));

        final Context context = newContextChain();
        Request request = buildAuthorizedRequest();
        filter.filter(context, request, nextHandler);

        AttributesContext attributesContext = context.asContext(AttributesContext.class);
        assertThat(attributesContext.getAttributes()).containsKey("myToken");
        assertThat(attributesContext.getAttributes().get("myToken")).isInstanceOf(AccessToken.class);
        verify(nextHandler).handle(context, request);
    }

    @Test
    public void shouldEvaluateScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilter filter =
                buildResourceServerFilter("${attributes.attribute}",
                                          "${split('to,b,or,not,to', ',')[1]}",
                                          "c");

        final AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("attribute", "a");
        Request request = buildAuthorizedRequest();
        filter.filter(context, request, nextHandler);

        verify(nextHandler).handle(context, request);
    }

    @Test
    public void shouldFailDueToInvalidScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilter filter = buildResourceServerFilter("${bad.attribute}");

        Request request = buildAuthorizedRequest();
        Response response = filter.filter(newContextChain(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).matches(".*scope expression \'.*\' could not be resolved");
    }

    private OAuth2ResourceServerFilter buildResourceServerFilter(String... scopes) throws ExpressionException {
        return new OAuth2ResourceServerFilter(resolver,
                                              new BearerTokenExtractor(),
                                              time,
                                              getScopes(scopes),
                                              DEFAULT_REALM_NAME,
                                              Expression.valueOf(
                                                      format("${attributes.%s}",
                                                      DEFAULT_ACCESS_TOKEN_KEY), String.class));
    }

    private static Context newContextChain() {
        return new AttributesContext(new RootContext());
    }

    private static Set<Expression<String>> getScopes(final String... scopes) throws ExpressionException {
        final Set<Expression<String>> expScopes = new HashSet<>(scopes.length);
        for (final String scope : scopes) {
            expScopes.add(Expression.valueOf(scope, String.class));
        }
        return expScopes;
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
