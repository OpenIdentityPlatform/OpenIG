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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.time.Duration.duration;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ThrottlingRateTest {

    @DataProvider
    public static Object[][] provideThrottlingRates() {
        //@Checkstyle:off
        return new Object[][]{
                { new ThrottlingRate(5, duration(1, SECONDS)), new ThrottlingRate(5, duration(1, SECONDS)) },
                { new ThrottlingRate(5, duration(1, SECONDS)), new ThrottlingRate(5, duration(1000, MILLISECONDS)) }
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "provideThrottlingRates")
    public void shouldEqual(ThrottlingRate left, ThrottlingRate right) throws Exception {
        assertThat(left).isEqualTo(right);
    }
}
