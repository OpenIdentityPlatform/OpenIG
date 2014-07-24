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
import static org.forgerock.openig.filter.oauth2.OAuth2TokenValidationFilter.*;
import static org.forgerock.openig.filter.oauth2.challenge.AuthenticateChallengeHandler.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Set;

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2TokenValidationFilterTest {

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
        when(token.getScopes()).thenReturn(asSet("a", "b", "c"));

        // By default consider all token as valid since their expiration time is greater than now
        when(token.getExpiresAt()).thenReturn(100L);
        when(time.now()).thenReturn(0L);
    }

    @Test
    public void shouldFailBecauseOfMissingHeader() throws Exception {
        OAuth2TokenValidationFilter filter = buildAuth2TokenValidationFilter();

        final Exchange exchange = buildUnAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.status).isEqualTo(401);
        assertThat(exchange.response.reason).isEqualTo("Unauthorized");
        assertThat(exchange.response.headers.getFirst(WWW_AUTHENTICATE))
                .isEqualTo(doubleQuote("Bearer realm='OpenIG'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfUnresolvableToken() throws Exception {
        when(resolver.resolve(TOKEN_ID))
                .thenThrow(new OAuth2TokenException("error"));

        OAuth2TokenValidationFilter filter = buildAuth2TokenValidationFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.status).isEqualTo(400);
        assertThat(exchange.response.reason).isEqualTo("Bad Request");
        assertThat(exchange.response.headers.getFirst(WWW_AUTHENTICATE))
                .contains(doubleQuote("error='invalid_request'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfExpiredToken() throws Exception {
        // Compared to the expiration date (100L), now is greater, so the token is expired
        when(time.now()).thenReturn(2000L);

        OAuth2TokenValidationFilter filter = buildAuth2TokenValidationFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.status).isEqualTo(401);
        assertThat(exchange.response.reason).isEqualTo("Unauthorized");
        assertThat(exchange.response.headers.getFirst(WWW_AUTHENTICATE))
                .contains(doubleQuote("error='invalid_token'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldFailBecauseOfMissingScopes() throws Exception {
        OAuth2TokenValidationFilter filter = buildAuth2TokenValidationFilter("a-missing-scope", "another-one");

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange.response.status).isEqualTo(403);
        assertThat(exchange.response.reason).isEqualTo("Forbidden");
        String header = exchange.response.headers.getFirst(WWW_AUTHENTICATE);
        assertThat(header).contains(doubleQuote("error='insufficient_scope'"));
        assertThat(header).contains(doubleQuote("scope='a-missing-scope another-one'"));
        verifyZeroInteractions(nextHandler);
    }

    @Test
    public void shouldStoreAccessTokenInTheExchange() throws Exception {
        OAuth2TokenValidationFilter filter = buildAuth2TokenValidationFilter();

        final Exchange exchange = buildAuthorizedExchange();
        filter.filter(exchange, nextHandler);

        assertThat(exchange).containsKey(ACCESS_TOKEN_KEY);
        verify(nextHandler).handle(exchange);
    }

    private OAuth2TokenValidationFilter buildAuth2TokenValidationFilter(String... scopes) {
        return new OAuth2TokenValidationFilter(resolver,
                                               new BearerTokenExtractor(),
                                               time,
                                               asSet(scopes),
                                               DEFAULT_REALM_NAME);
    }

    private static Set<String> asSet(final String... scopes) {
        return new HashSet<String>(asList(scopes));
    }

    private static Exchange buildUnAuthorizedExchange() {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        return exchange;
    }

    private static Exchange buildAuthorizedExchange() {
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.headers.add("Authorization", format("Bearer %s", TOKEN_ID));
        return exchange;
    }

    private static String doubleQuote(final String value) {
        return value.replaceAll("'", "\"");
    }

}
