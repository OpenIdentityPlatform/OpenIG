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
 * Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.resource.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.resource.core.routing.RoutingMode.EQUALS;
import static org.forgerock.resource.core.routing.RoutingMode.STARTS_WITH;
import static org.mockito.Mockito.mock;

import org.forgerock.resource.core.Context;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests {@link org.forgerock.resource.core.routing.UriRoute}.
 */
@SuppressWarnings("javadoc")
public final class UriRouteTest {

    @DataProvider
    public Object[][] testData() {
        return new Object[][] {
            // @formatter:off
            /* mode, template, resourceName, remaining */
            { EQUALS, "test", "test", "" },
            { EQUALS, "test/", "test", "" },
            { EQUALS, "test", "testremaining", null },
            { EQUALS, "users/{id}", "users/1", "" },
            { EQUALS, "users/{id}", "users/1/devices/0", null },
            { STARTS_WITH, "users/{id}", "users/1", "" },
            { STARTS_WITH, "users/{id}", "users/1/devices/0", "devices/0" },
            { STARTS_WITH, "test/", "test/remaining", "remaining" },
            { STARTS_WITH, "test/", "testremaining", null },
            { STARTS_WITH, "test/", "test", "" },
            { STARTS_WITH, "test/", "test/", "" },
            { STARTS_WITH, "test", "test/remaining", "remaining" },
            { STARTS_WITH, "test", "testremaining", null },
            { STARTS_WITH, "test{suffix}", "testabc", "" },
            { STARTS_WITH, "test{suffix}", "testabc/", "" },
            { STARTS_WITH, "test{suffix}", "testabc/123", "123" },
            { STARTS_WITH, "test", "test", "" },
            { STARTS_WITH, "test", "test/", "" },
            { STARTS_WITH, "", "", "" },
            { STARTS_WITH, "", "123", "123" },
            { STARTS_WITH, "", "123/456", "123/456" }
            // @formatter:on
        };
    }

    @Test(dataProvider = "testData")
    public void testGetRouteMatcher(final RoutingMode mode, final String template,
            final String resourceName, final String expectedRemaining) {
        UriRoute<Void> uriRoute = new UriRoute<Void>(mode, template, null);
        RouteMatcher matcher = uriRoute.getRouteMatcher(mock(Context.class), resourceName);
        if (expectedRemaining != null) {
            assertThat(matcher).isNotNull();
            assertThat(matcher.getRemaining()).isEqualTo(expectedRemaining);
        } else {
            assertThat(matcher).isNull();
        }
    }
}
