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

package org.forgerock.authz.modules.oauth2.cache;

import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.time.Duration.duration;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.authz.modules.oauth2.AccessTokenInfo;
import org.forgerock.authz.modules.oauth2.AccessTokenResolver;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.ThreadSafeCache;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A {@link CachingAccessTokenResolver} is a delegating {@link AccessTokenResolver} that uses a write-through cache
 * to enable fast {@link AccessTokenInfo} resolution.
 */
public class CachingAccessTokenResolver implements AccessTokenResolver {

    private final AccessTokenResolver resolver;
    private final ThreadSafeCache<String, Promise<AccessTokenInfo, AccessTokenException>> cache;
    private final AsyncFunction<Promise<AccessTokenInfo, AccessTokenException>, Duration, Exception> expires;

    /**
     * Builds a {@link CachingAccessTokenResolver} delegating to the given {@link AccessTokenResolver} using the given
     * (pre-configured) cache.
     *
     * @param time
     *         Time service used to compute the token cache time-to-live
     * @param resolver
     *         resolver to delegates to
     * @param cache
     *         access token cache
     */
    public CachingAccessTokenResolver(final TimeService time,
                                      final AccessTokenResolver resolver,
                                      final ThreadSafeCache
                                              <String, Promise<AccessTokenInfo, AccessTokenException>> cache) {
        this.resolver = resolver;
        this.cache = cache;
        this.expires = new AccessTokenExpirationFunction(time);
    }

    @Override
    public Promise<AccessTokenInfo, AccessTokenException> resolve(final Context context, final String token) {
        try {
            return cache.getValue(token, resolveToken(context, token), expires);
        } catch (InterruptedException e) {
            return newExceptionPromise(
                    new AccessTokenException("Timed out retrieving OAuth2 access token information", e));
        } catch (ExecutionException e) {
            return newExceptionPromise(
                    new AccessTokenException("Initial token resolution has failed", e));
        }
    }

    private Callable<Promise<AccessTokenInfo, AccessTokenException>> resolveToken(final Context context,
                                                                                  final String token) {
        return new Callable<Promise<AccessTokenInfo, AccessTokenException>>() {
            @Override
            public Promise<AccessTokenInfo, AccessTokenException> call() throws Exception {
                return resolver.resolve(context, token);
            }
        };
    }

    /**
     * A function that will compute the access token's timeout.
     */
    private static class AccessTokenExpirationFunction
            implements AsyncFunction<Promise<AccessTokenInfo, AccessTokenException>, Duration, Exception> {

        private static final Function<AccessTokenException, Duration, AccessTokenException> TIMEOUT_ZERO =
                new Function<AccessTokenException, Duration, AccessTokenException>() {
                    @Override
                    public Duration apply(AccessTokenException e) {
                        // Do not cache the AccessToken if there was a problem while
                        // resolving it
                        return Duration.ZERO;
                    }
                };

        private final Function<AccessTokenInfo, Duration, AccessTokenException> computeTtl;

        public AccessTokenExpirationFunction(final TimeService time) {
            this.computeTtl = new Function<AccessTokenInfo, Duration, AccessTokenException>() {
                @Override
                public Duration apply(AccessTokenInfo accessToken) {
                    if (accessToken.getExpiresAt() == AccessTokenInfo.NEVER_EXPIRES) {
                        return Duration.UNLIMITED;
                    }
                    long expires = accessToken.getExpiresAt() - time.now();
                    if (expires <= 0) {
                        // The token is already expired
                        return Duration.ZERO;
                    }

                    return duration(expires, TimeUnit.MILLISECONDS);
                }
            };
        }

        @Override
        public Promise<? extends Duration, ? extends Exception> apply(
                Promise<AccessTokenInfo, AccessTokenException> accessTokenPromise) throws Exception {
            return accessTokenPromise.then(computeTtl, TIMEOUT_ZERO);
        }

    }

}
