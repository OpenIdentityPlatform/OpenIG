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

package org.forgerock.openig.handler.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.handler.resources.MediaTypes.extensionOf;
import static org.forgerock.openig.handler.resources.MediaTypes.getMediaType;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MediaTypesTest {

    @DataProvider
    public static Object[][] expectedTypes() {
        // @Checkstyle:off
        return new Object[][] {
                { "asc", "application/pgp-signature" },
                { "sig", "application/pgp-signature" },
                { "GIF", "image/gif" }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "expectedTypes")
    public void shouldMapExtensionsToMediaTypes(String extension, String type) throws Exception {
        assertThat(getMediaType(extension)).isEqualTo(type);
    }

    @DataProvider
    public static Object[][] paths() {
        // @Checkstyle:off
        return new Object[][] {
                { "image.jpg", "jpg" },
                { "/path.with.dot/another.jpeg", "jpeg" },
                { ".m2", "m2" },
                { "no/extension", "" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "paths")
    public void shouldExtractExtension(String path, String expected) throws Exception {
        assertThat(extensionOf(path)).isEqualTo(expected);
    }
}
