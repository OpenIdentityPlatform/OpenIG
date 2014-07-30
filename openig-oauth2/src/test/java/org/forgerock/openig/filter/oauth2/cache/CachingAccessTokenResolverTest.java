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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.AccessTokenResolver;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CachingAccessTokenResolverTest {

    @Mock
    private AccessTokenResolver resolver;

    @Mock
    private ScheduledExecutorService executorService;

    // Don't know why but if I @Spy this field, Mockito does not re-creates the cache instance, leading to errors
    private ThreadSafeCache<String, AccessToken> cache;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        cache = spy(new ThreadSafeCache<String, AccessToken>(executorService));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseCache() throws Exception {
        CachingAccessTokenResolver caching = new CachingAccessTokenResolver(resolver, cache);

        AccessToken token = caching.resolve("TOKEN");
        AccessToken token2 = caching.resolve("TOKEN");

        assertThat(token).isSameAs(token2);
        verify(cache, times(2)).getValue(eq("TOKEN"), any(Callable.class));

    }
}
