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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import static java.lang.String.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.filter.oauth2.OAuth2ResourceServerFilter.*;
import static org.forgerock.openig.filter.oauth2.challenge.AuthenticateChallengeHandler.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2ResourceServerFilterTest {

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

    @Test
    public void shouldFailBecauseOfMissingHeader() throws Exception {
        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildUnAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(401);
        assertThat(exchange.response.getReason()).isEqualTo("Unauthorized");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .isEqualTo(doubleQuote("Bearer realm='OpenIG'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfUnresolvableToken() throws Exception {
        when(resolver.resolve(TOKEN_ID))
                .thenThrow(new OAuth2TokenException("error"));

        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(400);
        assertThat(exchange.response.getReason()).isEqualTo("Bad Request");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .contains(doubleQuote("error='invalid_request'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfExpiredToken() throws Exception {
        // Compared to the expiration date (100L), now is greater, so the token is expired
        when(time.now()).thenReturn(2000L);

        OAuth2ResourceServerFilter filter = buildResourceServerFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.getStatus()).isEqualTo(401);
        assertThat(exchange.response.getReason()).isEqualTo("Unauthorized");
        assertThat(exchange.response.getHeaders().getFirst(WWW_AUTHENTICATE))
                .contains(doubleQuote("error='invalid_token'"));
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
        assertThat(header).contains(doubleQuote("error='insufficient_scope'"));
        assertThat(header).contains(doubleQuote("scope='a-missing-scope another-one'"));
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
                new Expression("${exchange.myToken}"));

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
          expectedExceptionsMessageRegExp = ".*scope expression could not be resolved.*")
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
                                              new Expression(format("${exchange.%s}", DEFAULT_ACCESS_TOKEN_KEY)));
    }

    private static List<Expression> getScopes(final String... scopes) throws ExpressionException {
        final List<Expression> expScopes = new ArrayList<Expression>();
        for (final String scope : scopes) {
            expScopes.add(new Expression(scope));
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

}
