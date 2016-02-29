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

package org.forgerock.http.filter.throttling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;

import java.util.concurrent.TimeUnit;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FixedRateThrottlingPolicyTest {

    @Test
    public void shouldAlwaysReturnTheSameThrottlingRate() throws Exception {
        ThrottlingRate rate = new ThrottlingRate(41, duration(1, TimeUnit.MINUTES));

        FixedRateThrottlingPolicy policy = new FixedRateThrottlingPolicy(rate);

        ThrottlingRate returnedRate = policy.lookup(new RootContext(), new Request()).get();
        assertThat(returnedRate).isSameAs(rate);
    }
}
