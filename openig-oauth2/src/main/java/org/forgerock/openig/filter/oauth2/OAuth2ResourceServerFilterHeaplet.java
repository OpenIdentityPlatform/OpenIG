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
import static org.forgerock.http.filter.Filters.chainOf;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.setOf;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.http.oauth2.ResourceAccess;
import org.forgerock.http.oauth2.ResourceServerFilter;
import org.forgerock.http.oauth2.resolver.CachingAccessTokenResolver;
import org.forgerock.http.oauth2.resolver.OpenAmAccessTokenResolver;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.ConditionEnforcementFilter;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a {@link Request} that contains an OAuth 2.0 access token.
 * <p>
 * This filter expects an OAuth 2.0 token to be available in the HTTP {@literal Authorization} header:
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
 * If {@literal cacheExpiration} is not set, the default is to keep the {@link AccessTokenInfo} objects for 1 minute.
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
public class OAuth2ResourceServerFilterHeaplet extends GenericHeaplet {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2ResourceServerFilterHeaplet.class);

    /**
     * Name of the realm when none is specified in the heaplet.
     */
    public static final String DEFAULT_REALM_NAME = "OpenIG";

    private PerItemEvictionStrategyCache<String, Promise<AccessTokenInfo, AccessTokenException>> cache;

    @Override
    public Object create() throws HeapException {
        Handler httpHandler = config.get("providerHandler")
                                    .defaultTo(CLIENT_HANDLER_HEAP_KEY)
                                    .as(requiredHeapObject(heap, Handler.class));

        TimeService time = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
        AccessTokenResolver resolver = new OpenAmAccessTokenResolver(httpHandler,
                                                                     time,
                                                                     config.get("tokenInfoEndpoint")
                                                                           .as(evaluatedWithHeapProperties())
                                                                           .required()
                                                                           .asString());

        // Build the cache
        Duration expiration = config.get("cacheExpiration")
                                    .as(evaluatedWithHeapProperties())
                                    .defaultTo("1 minute")
                                    .as(duration());
        if (!expiration.isZero()) {
            ScheduledExecutorService executorService = config.get("executor")
                                                             .defaultTo(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY)
                                                             .as(requiredHeapObject(heap,
                                                                                    ScheduledExecutorService.class));
            cache = new PerItemEvictionStrategyCache<>(executorService, expiration);
            resolver = new CachingAccessTokenResolver(time, resolver, cache);
        }

        Set<Expression<String>> scopes = config.get("scopes")
                                               .required()
                                               .as(setOf(expression(String.class)));

        String realm = config.get("realm").as(evaluatedWithHeapProperties()).defaultTo(DEFAULT_REALM_NAME).asString();

        Filter filter = new ResourceServerFilter(resolver,
                                                 time,
                                                 new OpenIGResourceAccess(scopes),
                                                 realm);

        if (config.get("requireHttps")
                  .as(evaluatedWithHeapProperties())
                  .defaultTo(Boolean.TRUE)
                  .asBoolean()) {
            try {
                Expression<Boolean> expr = Expression.valueOf("${request.uri.scheme == 'https'}", Boolean.class);
                return chainOf(new ConditionEnforcementFilter(expr), filter);
            } catch (ExpressionException e) {
                // Can be ignored, since we completely control the expression
            }
        }
        return filter;
    }

    @Override
    public void destroy() {
        if (cache != null) {
            cache.clear();
        }
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
}
