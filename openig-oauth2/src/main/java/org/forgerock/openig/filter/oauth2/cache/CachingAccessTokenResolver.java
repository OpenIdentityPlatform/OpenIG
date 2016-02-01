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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.AccessTokenResolver;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;

/**
 * A {@link CachingAccessTokenResolver} is a delegating {@link AccessTokenResolver} that uses a write-through cache
 * to enable fast {@link AccessToken} resolution.
 */
public class CachingAccessTokenResolver implements AccessTokenResolver {

    private final AccessTokenResolver resolver;
    private final ThreadSafeCache<String, Promise<AccessToken, OAuth2TokenException>> cache;

    /**
     * Builds a {@link CachingAccessTokenResolver} delegating to the given {@link AccessTokenResolver} using the given
     * (pre-configured) cache.
     *
     * @param resolver
     *         resolver to delegates to
     * @param cache
     *         access token cache
     */
    public CachingAccessTokenResolver(final AccessTokenResolver resolver,
                                      final ThreadSafeCache<String, Promise<AccessToken, OAuth2TokenException>> cache) {
        this.resolver = resolver;
        this.cache = cache;
    }

    @Override
    public Promise<AccessToken, OAuth2TokenException> resolve(final Context context, final String token) {
        try {
            return cache.getValue(token, new Callable<Promise<AccessToken, OAuth2TokenException>>() {
                @Override
                public Promise<AccessToken, OAuth2TokenException> call() throws Exception {
                    return resolver.resolve(context, token);
                }
            }, expires());
        } catch (InterruptedException e) {
            return Promises.newExceptionPromise(
                    new OAuth2TokenException("Timed out retrieving OAuth2 access token information", e));
        } catch (ExecutionException e) {
            return Promises.newExceptionPromise(
                    new OAuth2TokenException("Initial token resolution has failed", e));
        }
    }

    private AsyncFunction<Promise<AccessToken, OAuth2TokenException>, Duration, Exception> expires() {
        return new AsyncFunction<Promise<AccessToken, OAuth2TokenException>, Duration, Exception>() {
            @Override
            public Promise<? extends Duration, ? extends Exception> apply(
                    Promise<AccessToken, OAuth2TokenException> accessTokenPromise)
                    throws Exception {
                return accessTokenPromise.then(new Function<AccessToken, Duration, OAuth2TokenException>() {
                    @Override
                    public Duration apply(AccessToken accessToken) throws OAuth2TokenException {
                        if (accessToken.getExpiresAt() == AccessToken.NEVER_EXPIRES) {
                            return Duration.duration("unlimited");
                        }
                        return new Duration(accessToken.getExpiresAt(), TimeUnit.MILLISECONDS);
                    }
                });
            }
        };
    }
}
