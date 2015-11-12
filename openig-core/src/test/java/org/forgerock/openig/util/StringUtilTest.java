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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.util.StringUtil.trailingSlash;
import static org.forgerock.openig.util.StringUtil.slug;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StringUtilTest {

    @DataProvider
    private static Object[][] validInputs() {
        return new Object[][] {
            { null, null },
            { "", "/" },
            { "http://www.example.com", "http://www.example.com/" },
            { "http://www.example.com/", "http://www.example.com/" } };
    }

    @Test(dataProvider = "validInputs")
    public void shouldAppendTrailingSlash(final String given, final String expected) {
        assertThat(trailingSlash(given)).isEqualTo(expected);
    }

    public static Object[][] slugs() {
        // @Checkstyle:off
        return new Object[][] {
                { null, null },
                { "", "" },
                { "0123456789", "0123456789" },
                { "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz" },
                { " lot of    spaces   ", "lot-of-spaces" },
                { "route.json", "routejson" },
                { "some-route-with--dashes  and -- whitespaces", "some-route-with-dashes-and-whitespaces" },
                { "{ClientHandler}/heap/2", "clienthandler-heap-2"},
                { "@&$§!*()[]{}£./|\\%", "" },
                { "éèêàôèïù", "eeeaoeiu" },
                { "^¨`", "" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "slugs")
    public void shouldPerformSlugConversion(String value, String expected) throws Exception {
        assertThat(slug(value)).isEqualTo(expected);
    }
}
