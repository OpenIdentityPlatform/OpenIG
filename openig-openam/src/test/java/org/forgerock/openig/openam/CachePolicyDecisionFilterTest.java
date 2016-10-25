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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.CachePolicyDecisionFilter.extractDurationFromTtl;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CachePolicyDecisionFilterTest {

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private TimeService timeService;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldUseTtlAsTimestamp() throws Exception {
        long now = 1_477_548_471_000L;
        when(timeService.now()).thenReturn(now);

        AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception> durationFromTtl =
                extractDurationFromTtl(duration(1, TimeUnit.MINUTES), timeService);
        long ttl = 1_477_549_196_000L;
        Duration expectedCacheDuration = duration(ttl - now, TimeUnit.MILLISECONDS);
        assertCacheDuration(durationFromTtl, expectedCacheDuration, ttl);
    }

    @Test
    public void shouldCacheWithDefaultTimeout() throws Exception {
        Duration defaultTimeout = duration(1, TimeUnit.MINUTES);
        AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception> durationFromTtl =
                extractDurationFromTtl(defaultTimeout, timeService);

        assertCacheDuration(durationFromTtl, defaultTimeout, Long.MAX_VALUE);
    }

    @Test
    public void shouldNotCacheBecauseTimestampIsOver() throws Exception {
        long now = 1_477_548_471_000L;
        when(timeService.now()).thenReturn(now);

        AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception> durationFromTtl =
                extractDurationFromTtl(duration(1, TimeUnit.MINUTES), timeService);

        assertCacheDuration(durationFromTtl, Duration.ZERO, now - 3);
    }

    private static void assertCacheDuration(
            AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception> durationFromTtl,
            Duration expectedCacheDuration,
            long ttl) throws Exception {
        JsonValue content = json(array(object(field("ttl", ttl))));
        Promise<ActionResponse, ResourceException> actionResponse = newActionResponse(content).asPromise();
        Duration cacheDuration = durationFromTtl.apply(actionResponse).getOrThrow();
        assertThat(cacheDuration).isEqualByComparingTo(expectedCacheDuration);
    }

}
