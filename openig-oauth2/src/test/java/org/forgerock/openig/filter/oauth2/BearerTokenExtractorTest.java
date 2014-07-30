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

package org.forgerock.openig.filter.oauth2;

import static org.assertj.core.api.Assertions.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BearerTokenExtractorTest {

    @DataProvider
    public Object[][] validHeaders() {
        // @Checkstyle:off
        return new Object[][] {
                // Multiple SP (whitespaces) as separator
                {"Bearer token"},
                //{"Bearer  token"}, TODO support multiple spaces

                // Trailing spaces should be ignored as per RFC 2616
                {"Bearer token    "},
                {"Bearer token  \t  "},

                // Ignore case of the auth-type
                {"BEARER token"},
                {"bearer token"},
                {"BeAReR token"}
        };
        // @Checkstyle:on
    }

    @DataProvider
    public Object[][] invalidHeaders() {
        // @Checkstyle:off
        return new Object[][] {
                // {"Bearer token1 token 2"}, The current code consider the rest of the string as a token
                // Invalid Separators
                {"Bearer\ttoken"},
                {"Bearer\t\ttoken"},
                // {"Bearer \ttoken"},  // \t is not part of the b64token allowed characters
                {"Bearer\t token"},
                {"unknown-type token"}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "validHeaders")
    public void shouldExtractTheTokenValue(String header) throws Exception {
        BearerTokenExtractor extractor = new BearerTokenExtractor();
        assertThat(extractor.getAccessToken(header)).isEqualTo("token");
    }

    @Test(dataProvider = "invalidHeaders")
    public void shouldNotReturnABearerToken(String header) throws Exception {
        BearerTokenExtractor extractor = new BearerTokenExtractor();
        assertThat(extractor.getAccessToken(header)).isNull();
    }
}
