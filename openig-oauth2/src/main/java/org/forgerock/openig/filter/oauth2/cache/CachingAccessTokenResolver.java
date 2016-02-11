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

package org.forgerock.openig.filter.oauth2.cache;

import static org.forgerock.util.time.Duration.duration;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.forgerock.authz.modules.oauth2.AccessToken;
import org.forgerock.authz.modules.oauth2.AccessTokenResolver;
import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A {@link CachingAccessTokenResolver} is a delegating {@link AccessTokenResolver} that uses a write-through cache
 * to enable fast {@link AccessToken} resolution.
 */
public class CachingAccessTokenResolver implements AccessTokenResolver {

    private final TimeService time;
    private final AccessTokenResolver resolver;
    private final ThreadSafeCache<String, Promise<AccessToken, AccessTokenException>> cache;

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
                                      final ThreadSafeCache<String, Promise<AccessToken, AccessTokenException>> cache) {
        this.time = time;
        this.resolver = resolver;
        this.cache = cache;
    }

    @Override
    public Promise<AccessToken, AccessTokenException> resolve(final Context context, final String token) {
        try {
            return cache.getValue(token, resolveToken(context, token), expires());
        } catch (InterruptedException e) {
            return Promises.newExceptionPromise(
                    new AccessTokenException("Timed out retrieving OAuth2 access token information", e));
        } catch (ExecutionException e) {
            return Promises.newExceptionPromise(
                    new AccessTokenException("Initial token resolution has failed", e));
        }
    }

    private Callable<Promise<AccessToken, AccessTokenException>> resolveToken(final Context context,
                                                                              final String token) {
        return new Callable<Promise<AccessToken, AccessTokenException>>() {
            @Override
            public Promise<AccessToken, AccessTokenException> call() throws Exception {
                return resolver.resolve(context, token);
            }
        };
    }

    private AsyncFunction<Promise<AccessToken, AccessTokenException>, Duration, Exception> expires() {
        return new AsyncFunction<Promise<AccessToken, AccessTokenException>, Duration, Exception>() {
            @Override
            public Promise<? extends Duration, ? extends Exception> apply(
                    Promise<AccessToken, AccessTokenException> accessTokenPromise)
                    throws Exception {
                return accessTokenPromise.then(new Function<AccessToken, Duration, AccessTokenException>() {
                    @Override
                    public Duration apply(AccessToken accessToken) throws AccessTokenException {
                        if (accessToken.getExpiresAt() == AccessToken.NEVER_EXPIRES) {
                            return duration("unlimited");
                        }
                        long expires = accessToken.getExpiresAt() - time.now();
                        if (expires <= 0) {
                            // The token is already expired
                            return duration("zero");
                        }

                        return new Duration(expires, TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
    }
}
