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

package org.forgerock.openig.filter.oauth2.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.AccessTokenResolver;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;

/**
 * A {@link CachingAccessTokenResolver} is a delegating {@link AccessTokenResolver} that uses a write-through cache
 * to enable fast {@link AccessToken} resolution.
 */
public class CachingAccessTokenResolver implements AccessTokenResolver {

    private final AccessTokenResolver resolver;
    private final ThreadSafeCache<String, AccessToken> cache;

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
                                      final ThreadSafeCache<String, AccessToken> cache) {
        this.resolver = resolver;
        this.cache = cache;
    }

    @Override
    public AccessToken resolve(final String token) throws OAuth2TokenException {
        try {
            return cache.getValue(token, new Callable<AccessToken>() {
                @Override
                public AccessToken call() throws Exception {
                    return resolver.resolve(token);
                }
            });
        } catch (InterruptedException e) {
            throw new OAuth2TokenException("interruption", e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new OAuth2TokenException("execution_error", e.getMessage(), e);
        }
    }
}
