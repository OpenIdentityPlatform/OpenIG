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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import static java.lang.String.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.util.Sets.*;
import static org.forgerock.openig.filter.oauth2.OAuth2ResourceServerFilter.*;
import static org.forgerock.openig.filter.oauth2.challenge.AuthenticateChallengeHandler.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.http.protocol.Request;
import org.assertj.core.api.Condition;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
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
        when(resolver.resolve(TOKEN_ID)).thenReturn(token);
        when(token.getScopes()).thenReturn(new HashSet<String>(asList("a", "b", "c")));

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

        final Exchange exchange = buildUnAuthorizedExchange();
        if (authorizationValue != null) {
            exchange.request.getHeaders().putSingle("Authorization", authorizationValue);
        }
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(401);
        assertThat(exchange.response.getReason()).isEqualTo("Unauthorized");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .isEqualTo(doubleQuote("Bearer realm='OpenIG'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfMultipleAuthorizationHeaders() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildUnAuthorizedExchange();
        exchange.request.getHeaders().add("Authorization", "Bearer 1234");
        exchange.request.getHeaders().add("Authorization", "Bearer 5678");
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(400);
        assertThat(exchange.response.getReason()).isEqualTo("Bad Request");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .startsWith(doubleQuote("Bearer realm='OpenIG', error='invalid_request'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfUnresolvableToken() throws Exception {
        when(resolver.resolve(TOKEN_ID))
                .thenThrow(new OAuth2TokenException("error"));
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    @Test
    public void shouldFailBecauseOfExpiredToken() throws Exception {
        // Compared to the expiration date (100L), now is greater, so the token is expired
        when(time.now()).thenReturn(2000L);
        runAndExpectUnauthorizedInvalidTokenResponse();
    }

    private void runAndExpectUnauthorizedInvalidTokenResponse() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(401);
        assertThat(exchange.response.getReason()).isEqualTo("Unauthorized");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .startsWith(doubleQuote("Bearer realm='OpenIG', error='invalid_token'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfMissingScopes() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter("a-missing-scope", "another-one");

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(403);
        assertThat(exchange.response.getReason()).isEqualTo("Forbidden");
        String header = exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE);
        assertThat(header).has(scopes("another-one", "a-missing-scope"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldStoreAccessTokenInTheExchange() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange).containsKey(DEFAULT_ACCESS_TOKEN_KEY);
        verify(nextHandler).handle(exchange);
    }

    @Test
    public void shouldStoreAccessTokenInTargetInTheExchange() throws Exception {
        final OAuth2ResourceServerFilter filter = new OAuth2ResourceServerFilter(resolver,
                new BearerTokenExtractor(),
                time,
                Expression.valueOf("${exchange.myToken}"));

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange).containsKey("myToken");
        assertThat(exchange.get("myToken")).isInstanceOf(AccessToken.class);
        verify(nextHandler).handle(exchange);
    }

    @Test
    public void shouldEvaluateScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilter filter =
                buildResourceServerFilter("${exchange.attribute}",
                                          "${split('to,b,or,not,to', ',')[1]}",
                                          "c");

        final Exchange exchange = buildAuthorizedExchange();
        exchange.put("attribute", "a");
        filter.filter(exchange, nextHandler);

        verify(nextHandler).handle(exchange);
    }

    @Test(expectedExceptions = HandlerException.class,
          expectedExceptionsMessageRegExp = ".*scope expression \'.*\' could not be resolved")
    public void shouldFailDueToInvalidScopeExpressions() throws Exception {
        final OAuth2ResourceServerFilter filter = buildResourceServerFilter("${bad.attribute}");

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);
    }

    private OAuth2ResourceServerFilter buildResourceServerFilter(String... scopes) throws ExpressionException {
        return new OAuth2ResourceServerFilter(resolver,
                                              new BearerTokenExtractor(),
                                              time,
                                              getScopes(scopes),
                                              DEFAULT_REALM_NAME,
                                              Expression.valueOf(format("${exchange.%s}", DEFAULT_ACCESS_TOKEN_KEY)));
    }

    private static Set<Expression> getScopes(final String... scopes) throws ExpressionException {
        final Set<Expression> expScopes = new HashSet<Expression>(scopes.length);
        for (final String scope : scopes) {
            expScopes.add(Expression.valueOf(scope));
        }
        return expScopes;
    }

    private static Exchange buildUnAuthorizedExchange() {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        return exchange;
    }

    private static Exchange buildAuthorizedExchange() {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.getHeaders().add("Authorization", format("Bearer %s", TOKEN_ID));
        return exchange;
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
