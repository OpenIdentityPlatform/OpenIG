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
 * Portions Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openig.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.el.Bindings.bindings;

import java.time.Instant;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ExpressionInstantTest {
    @Test
    public void testExpressionInstant() throws ExpressionException {
        ExpressionInstant instant = Expression.valueOf("${now}", ExpressionInstant.class).eval(bindings());
        assertThat(instant).isNotNull();
        assertThat(instant.getEpochMillis()).isGreaterThan(0);
    }

    @DataProvider
    public Object[][] timeAdditions() {
        return new Object[][] {
            { "plusDays(1).epochSeconds", 86_400L },
            { "plusHours(1).epochSeconds", 3_600L },
            { "plusMinutes(1).epochSeconds", 60L },
            { "plusSeconds(1).epochSeconds", 1L },
            { "plusMillis(1).epochMillis", 1L }
        };
    }

    @Test(dataProvider = "timeAdditions")
    public void testExpressionInstantAddition(String operation, long expected) throws ExpressionException {
        Bindings bindings = bindings("instant", new ExpressionInstant(Instant.EPOCH));
        Long result = Expression.valueOf("${instant." + operation + "}", Long.class).eval(bindings);
        assertThat(result).isEqualTo(expected);
    }
}
