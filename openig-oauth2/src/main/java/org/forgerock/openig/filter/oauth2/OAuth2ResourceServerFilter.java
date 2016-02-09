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
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.getWithDeprecation;
import static org.forgerock.openig.util.JsonValues.ofExpression;
import static org.forgerock.util.time.Duration.duration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.authz.modules.oauth2.AccessToken;
import org.forgerock.authz.modules.oauth2.ResourceAccess;
import org.forgerock.authz.modules.oauth2.AccessTokenResolver;
import org.forgerock.authz.modules.oauth2.ResourceServerFilter;
import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.oauth2.cache.CachingAccessTokenResolver;
import org.forgerock.openig.filter.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * Validates a {@link Request} that contains an OAuth 2.0 access token. <p> This filter expects an OAuth 2.0 token to
 * be available in the HTTP {@literal Authorization} header:
 *
 * <pre>{@code Authorization: Bearer 1fc0e143-f248-4e50-9c13-1d710360cec9}</pre>
 *
 * It extracts the token and validate it against the {@literal tokenInfoEndpoint} URL provided in the configuration.
 *
 * <pre>
 * {@code
 * {
 *         "name": "ProtectedResourceFilter",
 *         "type": "OAuth2ResourceServerFilter",
 *         "config": {
 *           "scopes": [ "email", "profile" ],
 *           "tokenInfoEndpoint": "https://openam.example.com:8443/openam/oauth2/tokeninfo",
 *           "cacheExpiration": "2 minutes",
 *           "requireHttps": false,
 *           "providerHandler": "ClientHandler",
 *           "realm": "Informative realm name",
 *         }
 * }
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
 *     {@code
 *     "cacheExpiration": "2 minutes"
 *     "cacheExpiration": "3 days and 6 hours"
 *     "cacheExpiration": "5m" // 5 minutes
 *     "cacheExpiration": "10 min, 30 sec"
 *     "cacheExpiration": "zero" // no cache
 *     "cacheExpiration": "0 s" // no cache
 *     }
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
 *
 * @see Duration
 */
public class OAuth2ResourceServerFilter extends GenericHeapObject implements Filter {

    /**
     * Name of the realm when none is specified in the heaplet.
     */
    public static final String DEFAULT_REALM_NAME = "OpenIG";

    private final ResourceServerFilter delegate;

    /**
     * Creates a new {@code OAuth2Filter}.
     *
     * @param delegate
     *         The {@link ResourceServerFilter} to delegate the request.
     */
    public OAuth2ResourceServerFilter(final ResourceServerFilter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        return delegate.filter(context, request, next);
    }

    static final class OpenIGResourceAccess implements ResourceAccess {
        private final Set<Expression<String>> scopes;

        OpenIGResourceAccess(final Set<Expression<String>> scopes) {
            this.scopes = scopes;
        }

        @Override
        public Set<String> getRequiredScopes(final Context context, final Request request) throws ResponseException {
            final Set<String> scopeValues = new HashSet<>(scopes.size());
            for (final Expression<String> scope : scopes) {
                final String result = scope.eval(bindings(context, request));
                if (result == null) {
                    throw new ResponseException(format(
                            "The OAuth 2.0 resource server filter scope expression '%s' could not be resolved",
                            scope.toString()));
                }
                scopeValues.add(result);
            }
            return scopeValues;
        }
    }

    /** Creates and initializes an OAuth2 filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private ThreadSafeCache<String, Promise<AccessToken, AccessTokenException>> cache;
        private ScheduledExecutorService executorService;

        @Override
        public Object create() throws HeapException {
            Handler httpHandler = heap.resolve(getWithDeprecation(config, logger, "providerHandler", "httpHandler")
                                           .defaultTo(CLIENT_HANDLER_HEAP_KEY),
                                  Handler.class);

            TimeService time = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
            AccessTokenResolver resolver = new OpenAmAccessTokenResolver(
                    httpHandler,
                    time,
                    config.get("tokenInfoEndpoint").required().asString());

            // Build the cache
            Duration expiration = duration(config.get("cacheExpiration").defaultTo("1 minute").asString());
            if (!expiration.isZero()) {
                executorService = Executors.newSingleThreadScheduledExecutor();
                cache = new ThreadSafeCache<>(executorService);
                cache.setDefaultTimeout(expiration);
                resolver = new CachingAccessTokenResolver(resolver, cache);
            }

            Set<Expression<String>> scopes =
                    getWithDeprecation(config, logger, "scopes", "requiredScopes").required().asSet(ofExpression());

            String realm = config.get("realm").defaultTo(DEFAULT_REALM_NAME).asString();

            final OAuth2ResourceServerFilter filter = new OAuth2ResourceServerFilter(
                    new ResourceServerFilter(resolver,
                                             time,
                                             new OpenIGResourceAccess(scopes),
                                             realm));

            if (getWithDeprecation(config, logger, "requireHttps", "enforceHttps").defaultTo(
                    Boolean.TRUE).asBoolean()) {
                try {
                    Expression<Boolean> expr = Expression.valueOf("${request.uri.scheme == 'https'}", Boolean.class);
                    return new EnforcerFilter(expr, filter, logger);
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
