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
import static org.forgerock.openig.heap.HeapUtil.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.Filter;
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
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.log.LogTimer;
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
 * Here is a sample configuration:
 * <pre>
 *     {@code
 * {
 *         "name": "OAuth2Filter",
 *         "type": "org.forgerock.openig.filter.oauth2.OAuth2TokenValidationFilter",
 *         "config": {
 *           "requiredScopes": [ "email", "profile" ],
 *           "tokenInfoEndpoint": "https://openam.example.com:8443/openam/oauth2/tokeninfo",
 *           "cacheExpiration": "2 minutes",
 *           "enforceHttps": false,
 *           "httpHandler": "ClientHandler",
 *           "realm": "Informative realm name"
 *         }
 *       }
 *     }
 * </pre>
 *
 * {@literal requiredScopes}, {@literal tokenInfoEndpoint} and {@literal httpHandler} are the 3 only mandatory
 * configuration attributes.
 * <p>
 * If {@literal cacheExpiration} is not set, the default is to keep the {@link AccessToken}s for 1 minute.
 * {@literal cacheExpiration} is expressed using natural language:
 * <pre>
 *     "cacheExpiration": "2 minutes"
 *     "cacheExpiration": "3 days and 6 hours"
 *     "cacheExpiration": "5m" // 5 minutes
 *     "cacheExpiration": "10 min, 30 sec"
 * </pre>
 * <p>
 * {@literal httpHandler} is a name reference to another handler available in the heap. It will be used to perform
 * access token validation against the {@literal tokenInfoEndpoint} URL.
 * It is usually a reference to some {@link org.forgerock.openig.handler.ClientHandler}.
 * <p>
 * The {@literal enforceHttps} optional attribute control if this filter only accepts requests targeting the HTTPS
 * scheme. By default, it is enabled (only URI starting with {@literal https://...} will be accepted,
 * an Exception is thrown otherwise).
 * <p>
 * The {@literal realm} optional attribute specifies the name of the realm used in the authentication challenges
 * returned back to the client in case of errors.
 *
 * @see Duration
 */
public class OAuth2TokenValidationFilter extends GenericFilter {

    /**
     * The key under which downstream handlers will find the access token in the {@link Exchange}.
     */
    public static final String ACCESS_TOKEN_KEY = "oauth2AccessToken";

    /**
     * Name of the realm when none is specified in the heaplet.
     */
    public static final String DEFAULT_REALM_NAME = "OpenIG";

    private final AccessTokenResolver resolver;
    private final BearerTokenExtractor extractor;
    private final TimeService time;
    private final Set<String> scopes;

    private final Handler noAuthentication;
    private final Handler invalidToken;
    private final Handler invalidRequest;
    private final Handler insufficientScope;

    /**
     * Creates a new {@code OAuth2Filter}.
     *
     * @param resolver
     *         A {@code AccessTokenResolver} instance.
     * @param extractor
     *         A {@code BearerTokenExtractor} instance.
     * @param time
     *         A {@link TimeService} instance used to check if token is expired or not.
     */
    public OAuth2TokenValidationFilter(final AccessTokenResolver resolver,
                                       final BearerTokenExtractor extractor,
                                       final TimeService time) {
        this(resolver, extractor, time, Collections.<String>emptySet(), DEFAULT_REALM_NAME);
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
     *         A set of scopes to be checked in the resolved access tokens.
     * @param realm
     *         Name of the realm (used in authentication challenge returned in case of error).
     */
    public OAuth2TokenValidationFilter(final AccessTokenResolver resolver,
                                       final BearerTokenExtractor extractor,
                                       final TimeService time,
                                       final Set<String> scopes,
                                       final String realm) {
        this.resolver = resolver;
        this.extractor = extractor;
        this.time = time;
        this.scopes = scopes;
        this.noAuthentication = new NoAuthenticationChallengeHandler(realm);
        this.invalidToken = new InvalidTokenChallengeHandler(realm);
        this.invalidRequest = new InvalidRequestChallengeHandler(realm);
        this.insufficientScope = new InsufficientScopeChallengeHandler(realm, scopes);
    }

    @Override
    public void filter(final Exchange exchange, final Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        try {
            String token = getAccessToken(exchange.request);
            if (token == null) {
                logger.debug("Missing OAuth 2.0 Bearer Token Authorization header");
                noAuthentication.handle(exchange);
                return;
            }

            // Resolve the token
            AccessToken accessToken;
            try {
                accessToken = resolver.resolve(token);
            } catch (OAuth2TokenException e) {
                logger.debug(format("Cannot authorize request with token '%s' because [error:%s, description:%s]",
                                    token,
                                    e.getError(),
                                    e.getDescription()));
                invalidRequest.handle(exchange);
                return;
            }

            // Validate the token (expiration + scopes)
            if (isExpired(accessToken)) {
                logger.debug(format("Access Token '%s' is expired", token));
                invalidToken.handle(exchange);
                return;
            }

            if (areRequiredScopesMissing(accessToken)) {
                logger.debug(format("Access Token '%s' is missing required scopes", token));
                insufficientScope.handle(exchange);
                return;
            }

            // Store the AccessToken in the exchange for downstream handlers
            exchange.put(ACCESS_TOKEN_KEY, accessToken);

            // Call the rest of the chain
            next.handle(exchange);
        } finally {
            timer.stop();
        }
    }

    private boolean isExpired(final AccessToken accessToken) {
        return time.now() > accessToken.getExpiresAt();
    }

    private boolean areRequiredScopesMissing(final AccessToken accessToken) {
        return !accessToken.getScopes().containsAll(this.scopes);
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
    private String getAccessToken(final Request request) {
        String header = request.headers.getFirst("Authorization");
        return extractor.getAccessToken(header);
    }

    /** Creates and initializes an OAuth2 filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {

        private ThreadSafeCache<String, AccessToken> cache;
        private ScheduledExecutorService executorService;

        @Override
        public Object create() throws HeapException {

            Handler httpHandler = getRequiredObject(heap,
                                                    config.get("httpHandler").required(),
                                                    Handler.class);
            TimeService time = TimeService.SYSTEM;
            AccessTokenResolver resolver = new OpenAmAccessTokenResolver(
                    httpHandler,
                    time,
                    config.get("tokenInfoEndpoint").required().asString());

            // Build the cache
            Duration expiration = new Duration(config.get("cacheExpiration").defaultTo("1 minute").asString());
            executorService = Executors.newSingleThreadScheduledExecutor();
            cache = new ThreadSafeCache<String, AccessToken>(executorService);
            cache.setTimeout(expiration);
            resolver = new CachingAccessTokenResolver(resolver, cache);

            HashSet<String> scopes = new HashSet<String>(config.get("requiredScopes").required().asList(String.class));

            String realm = config.get("realm").defaultTo(DEFAULT_REALM_NAME).asString();

            Filter filter = new OAuth2TokenValidationFilter(resolver,
                                                            new BearerTokenExtractor(),
                                                            time,
                                                            scopes,
                                                            realm);
            if (config.get("enforceHttps").defaultTo(Boolean.TRUE).asBoolean()) {
                try {
                    filter = new EnforcerFilter(new Expression("${exchange.request.uri.scheme == 'https'}"),
                                                filter);
                } catch (ExpressionException e) {
                    // Can be ignored, since we completely control the expression
                }
            }
            return filter;
        }

        @Override
        public void destroy() {
            executorService.shutdownNow();
            cache.clear();
        }
    }

}
