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
import static org.forgerock.openig.util.Duration.*;
import static org.forgerock.openig.util.Json.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Request;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.GenericFilter;
import org.forgerock.openig.filter.oauth2.cache.CachingAccessTokenResolver;
import org.forgerock.openig.filter.oauth2.cache.ThreadSafeCache;
import org.forgerock.openig.filter.oauth2.challenge.InsufficientScopeChallengeHandler;
import org.forgerock.openig.filter.oauth2.challenge.InvalidRequestChallengeHandler;
import org.forgerock.openig.filter.oauth2.challenge.InvalidTokenChallengeHandler;
import org.forgerock.openig.filter.oauth2.challenge.NoAuthenticationChallengeHandler;
import org.forgerock.openig.filter.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.http.Headers;
import org.forgerock.openig.util.Duration;
import org.forgerock.util.time.TimeService;

/**
 * Validates an {@link Exchange} that contains an OAuth 2.0 access token. <p> This filter expects an OAuth 2.0 token to
 * be available in the HTTP {@literal Authorization} header:
 *
 * <pre>{@code Authorization: Bearer 1fc0e143-f248-4e50-9c13-1d710360cec9}</pre>
 *
 * It extracts the token and validate it against the {@literal token-info-endpoint} URL provided in the configuration.
 *
 * <pre>
 * {
 *         "name": "ProtectedResourceFilter",
 *         "type": "org.forgerock.openig.filter.oauth2.OAuth2ResourceServerFilter",
 *         "config": {
 *           "scopes": [ "email", "profile" ],
 *           "tokenInfoEndpoint": "https://openam.example.com:8443/openam/oauth2/tokeninfo",
 *           "cacheExpiration": "2 minutes",
 *           "requireHttps": false,
 *           "providerHandler": "ClientHandler",
 *           "realm": "Informative realm name",
 *           "target": "${exchange.oauth2AccessToken}"
 *         }
 * }
 * </pre>
 *
 * {@literal scopes}, {@literal tokenInfoEndpoint} and {@literal providerHandler} are the 3 only mandatory
 * configuration attributes.
 * <p>
 * If {@literal cacheExpiration} is not set, the default is to keep the {@link AccessToken}s for 1 minute.
 * {@literal cacheExpiration} is expressed using natural language (use {@literal zero} or {@literal none}
 * to deactivate caching, any 0 valued duration will also deactivate it):
 * <pre>
 *     "cacheExpiration": "2 minutes"
 *     "cacheExpiration": "3 days and 6 hours"
 *     "cacheExpiration": "5m" // 5 minutes
 *     "cacheExpiration": "10 min, 30 sec"
 *     "cacheExpiration": "zero" // no cache
 *     "cacheExpiration": "0 s" // no cache
 * </pre>
 * <p>
 * {@literal providerHandler} is a name reference to another handler available in the heap. It will be used to perform
 * access token validation against the {@literal tokenInfoEndpoint} URL.
 * It is usually a reference to some {@link org.forgerock.openig.handler.ClientHandler}.
 * <p>
 * The {@literal requireHttps} optional attribute control if this filter only accepts requests targeting the HTTPS
 * scheme. By default, it is enabled (only URI starting with {@literal https://...} will be accepted,
 * an Exception is thrown otherwise).
 * <p>
 * The {@literal realm} optional attribute specifies the name of the realm used in the authentication challenges
 * returned back to the client in case of errors.
 * <p>
 * The {@literal target} optional attribute specifies the expression which will be used for storing the OAuth 2.0 access
 * token information in the exchange. Defaults to <tt>${exchange.oauth2AccessToken}</tt>.
 *
 * @see Duration
 */
public class OAuth2ResourceServerFilter extends GenericFilter {

    /**
     * The key under which downstream handlers will find the access token in the {@link Exchange}.
     */
    public static final String DEFAULT_ACCESS_TOKEN_KEY = "oauth2AccessToken";

    /**
     * Name of the realm when none is specified in the heaplet.
     */
    public static final String DEFAULT_REALM_NAME = "OpenIG";

    private final AccessTokenResolver resolver;
    private final BearerTokenExtractor extractor;
    private final TimeService time;
    private Set<Expression> scopes;
    private String realm;

    private final Handler noAuthentication;
    private final Handler invalidToken;
    private final Handler invalidRequest;
    private final Expression target;

    /**
     * Creates a new {@code OAuth2Filter}.
     *
     * @param resolver
     *         A {@code AccessTokenResolver} instance.
     * @param extractor
     *         A {@code BearerTokenExtractor} instance.
     * @param time
     *         A {@link TimeService} instance used to check if token is expired or not.
     * @param target
     *            The {@literal target} optional attribute specifies the expression which will be used for storing the
     *            OAuth 2.0 access token information in the exchange. Should not be null.
     */
    public OAuth2ResourceServerFilter(final AccessTokenResolver resolver,
                                      final BearerTokenExtractor extractor,
                                      final TimeService time,
                                      final Expression target) {
        this(resolver, extractor, time, Collections.<Expression> emptySet(), DEFAULT_REALM_NAME, target);
    }

    /**
     * Creates a new {@code OAuth2Filter}.
     *
     * @param resolver
     *         A {@code AccessTokenResolver} instance.
     * @param extractor
     *         A {@code BearerTokenExtractor} instance.
     * @param time
     *         A {@link TimeService} instance used to check if token is expired or not.
     * @param scopes
     *         A set of scope expressions to be checked in the resolved access tokens.
     * @param realm
     *         Name of the realm (used in authentication challenge returned in case of error).
     * @param target
     *            The {@literal target} optional attribute specifies the expression which will be used for storing the
     *            OAuth 2.0 access token information in the exchange. Should not be null.
     */
    public OAuth2ResourceServerFilter(final AccessTokenResolver resolver,
                                      final BearerTokenExtractor extractor,
                                      final TimeService time,
                                      final Set<Expression> scopes,
                                      final String realm,
                                      final Expression target) {
        this.resolver = resolver;
        this.extractor = extractor;
        this.time = time;
        this.scopes = scopes;
        this.realm = realm;
        this.noAuthentication = new NoAuthenticationChallengeHandler(realm);
        this.invalidToken = new InvalidTokenChallengeHandler(realm);
        this.invalidRequest = new InvalidRequestChallengeHandler(realm);
        this.target = target;
    }

    @Override
    public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
        String token = null;
        try {
            token = getAccessToken(exchange.request);
            if (token == null) {
                logger.debug("Missing OAuth 2.0 Bearer Token in the Authorization header");
                noAuthentication.handle(exchange);
                return;
            }
        } catch (OAuth2TokenException e) {
            logger.debug("Multiple 'Authorization' headers in the request");
            logger.debug(e);
            invalidRequest.handle(exchange);
            return;
        }

        // Resolve the token
        AccessToken accessToken;
        try {
            accessToken = resolver.resolve(token);
        } catch (OAuth2TokenException e) {
            logger.debug(format("Access Token '%s' cannot be resolved", token));
            logger.debug(e);
            invalidToken.handle(exchange);
            return;
        }

        // Validate the token (expiration + scopes)
        if (isExpired(accessToken)) {
            logger.debug(format("Access Token '%s' is expired", token));
            invalidToken.handle(exchange);
            return;
        }

        final Set<String> setOfScopes = getScopes(exchange);
        if (areRequiredScopesMissing(accessToken, setOfScopes)) {
            logger.debug(format("Access Token '%s' is missing required scopes", token));
            new InsufficientScopeChallengeHandler(realm, setOfScopes).handle(exchange);
            return;
        }

        // Store the AccessToken in the exchange for downstream handlers
        target.set(exchange, accessToken);

        // Call the rest of the chain
        next.handle(exchange);
    }

    private boolean isExpired(final AccessToken accessToken) {
        return time.now() > accessToken.getExpiresAt();
    }

    private boolean areRequiredScopesMissing(final AccessToken accessToken, final Set<String> scopes)
            throws HandlerException {
        return !accessToken.getScopes().containsAll(scopes);
    }

    private Set<String> getScopes(final Exchange exchange) throws HandlerException {
        final Set<String> scopeValues = new HashSet<String>(this.scopes.size());
        for (final Expression scope : this.scopes) {
            final String result = scope.eval(exchange, String.class);
            if (result == null) {
                throw new HandlerException(
                        "The OAuth 2.0 resource server filter scope expression could not be resolved");
            }
            scopeValues.add(result);
        }
        return scopeValues;
    }

    /**
     * Pulls the access token off of the request, by looking for the {@literal Authorization} header containing a
     * {@literal Bearer} token.
     *
     * @param request
     *         The Http {@link Request} message.
     * @return The access token, or {@literal null} if the access token was not present or was not using {@literal
     * Bearer} authorization.
     */
    private String getAccessToken(final Request request) throws OAuth2TokenException {
        Headers headers = request.getHeaders();
        List<String> authorizations = headers.get("Authorization");
        if ((authorizations != null) && (authorizations.size() >= 2)) {
            throw new OAuth2TokenException("Can't use more than 1 'Authorization' Header to convey"
                                                   + " the OAuth2 AccessToken");
        }
        String header = headers.getFirst("Authorization");
        return extractor.getAccessToken(header);
    }

    /** Creates and initializes an OAuth2 filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private ThreadSafeCache<String, AccessToken> cache;
        private ScheduledExecutorService executorService;

        @Override
        public Object create() throws HeapException {
            Handler httpHandler =
                    heap.resolve(getWithDeprecation(config, logger, "providerHandler",
                            "httpHandler").required(), Handler.class);

            TimeService time = TimeService.SYSTEM;
            AccessTokenResolver resolver = new OpenAmAccessTokenResolver(
                    httpHandler,
                    time,
                    config.get("tokenInfoEndpoint").required().asString());

            // Build the cache
            Duration expiration = duration(config.get("cacheExpiration").defaultTo("1 minute").asString());
            if (!expiration.isZero()) {
                executorService = Executors.newSingleThreadScheduledExecutor();
                cache = new ThreadSafeCache<String, AccessToken>(executorService);
                cache.setTimeout(expiration);
                resolver = new CachingAccessTokenResolver(resolver, cache);
            }

            Set<Expression> scopes =
                    getWithDeprecation(config, logger, "scopes", "requiredScopes").required().asSet(ofExpression());

            String realm = config.get("realm").defaultTo(DEFAULT_REALM_NAME).asString();

            final Expression target = asExpression(config.get("target").defaultTo(
                    format("${exchange.%s}", DEFAULT_ACCESS_TOKEN_KEY)));

            final OAuth2ResourceServerFilter filter = new OAuth2ResourceServerFilter(resolver,
                                                           new BearerTokenExtractor(),
                                                           time,
                                                           scopes,
                                                           realm,
                                                           target);

            if (getWithDeprecation(config, logger, "requireHttps", "enforceHttps").defaultTo(
                    Boolean.TRUE).asBoolean()) {
                try {
                    return new EnforcerFilter(Expression.valueOf("${exchange.request.uri.scheme == 'https'}"), filter);
                } catch (ExpressionException e) {
                    // Can be ignored, since we completely control the expression
                }
            }
            return filter;
        }

        @Override
        public void destroy() {
            if (executorService != null) {
                executorService.shutdownNow();
            }
            if (cache != null) {
                cache.clear();
            }
        }
    }

}
