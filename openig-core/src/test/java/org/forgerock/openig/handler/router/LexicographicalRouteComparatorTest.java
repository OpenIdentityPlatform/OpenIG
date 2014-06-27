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

package org.forgerock.openig.handler.router;

import static org.assertj.core.api.Assertions.*;

import org.forgerock.openig.heap.HeapImpl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LexicographicalRouteComparatorTest {

    @DataProvider
    public Object[][] routes() {
        // @Checkstyle:off
        return new Object[][] {
                { "a", "b" },
                { "a", "ab" },
                { "0", "1" },
                { "00", "042" },
                { "00-myroute.json", "a-route.json" },
                { "abc", "b" }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "routes")
    public void testName(final String min, final String max) throws Exception {
        LexicographicalRouteComparator comparator = new LexicographicalRouteComparator();

        Route a = new Route(new HeapImpl(), null, min, null, null);
        Route b = new Route(new HeapImpl(), null, max, null, null);

        assertThat(comparator.compare(a, b)).isLessThan(0);
        assertThat(comparator.compare(b, a)).isGreaterThan(0);
        assertThat(comparator.compare(a, a)).isEqualTo(0);
        assertThat(comparator.compare(b, b)).isEqualTo(0);
    }

}
