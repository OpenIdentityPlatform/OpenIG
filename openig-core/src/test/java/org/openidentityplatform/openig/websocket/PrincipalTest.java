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
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.protocol.Status;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PrincipalTest {

    @Test
    public void shouldUseConfiguredTtlInSeconds() {
        assertThat(Principal.ttlMillis("5")).isEqualTo(5_000L);
        assertThat(Principal.ttlMillis(" 42 ")).isEqualTo(42_000L);
    }

    @Test
    public void shouldFallBackToDefaultTtlWhenPropertyIsMissingOrInvalid() {
        assertThat(Principal.ttlMillis(null)).isEqualTo(Principal.DEFAULT_TTL_SECONDS * 1000);
        assertThat(Principal.ttlMillis("soon")).isEqualTo(Principal.DEFAULT_TTL_SECONDS * 1000);
        assertThat(Principal.ttlMillis("")).isEqualTo(Principal.DEFAULT_TTL_SECONDS * 1000);
    }

    @Test
    public void shouldReauthorizeWithTtlReadAtClassLoad() {
        final Principal principal = new Principal(null, null);
        principal.res = Status.OK;
        principal.lastAuthorize = 1_000L;
        // Changing the property after class load must not affect the interval
        final String previous = System.setProperty(Principal.TTL_PROPERTY, "0");
        try {
            assertThat(principal.expired(1_000L + Principal.TTL_MILLIS - 1)).isFalse();
            assertThat(principal.expired(1_000L + Principal.TTL_MILLIS)).isTrue();
        } finally {
            if (previous == null) {
                System.clearProperty(Principal.TTL_PROPERTY);
            } else {
                System.setProperty(Principal.TTL_PROPERTY, previous);
            }
        }
    }

    @Test
    public void shouldAuthorizeFromCacheWithTtlReadAtClassLoad() throws Exception {
        final Principal principal = new Principal(null, null);
        principal.res = Status.OK;
        principal.lastAuthorize = System.currentTimeMillis();
        // A per-call read of "0" would re-authorize through the gateway servlet instead of the cache
        final String previous = System.setProperty(Principal.TTL_PROPERTY, "0");
        try {
            assertThat(principal.authorize()).isSameAs(Status.OK);
        } finally {
            if (previous == null) {
                System.clearProperty(Principal.TTL_PROPERTY);
            } else {
                System.setProperty(Principal.TTL_PROPERTY, previous);
            }
        }
    }

    @Test
    public void shouldExpireWithoutCachedResult() {
        assertThat(new Principal(null, null).expired(1_000L)).isTrue();
    }
}
