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
}
