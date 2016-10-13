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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.openam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.MockitoAnnotations.initMocks;

import org.forgerock.json.JsonValue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SsoTokenContextTest {

    private static final String TOKEN = "ARrrg...42*";
    private SsoTokenContext ssoTokenContext;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        ssoTokenContext = new SsoTokenContext(null, validSsoToken(), TOKEN);
    }

    @DataProvider
    private static Object[][] invalidParameters() {
        return new Object[][]{
            { null, TOKEN },
            { validSsoToken(), null }
        };
    }

    @Test(dataProvider = "invalidParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailWithNullParameter(final JsonValue info, final String token) {
        new SsoTokenContext(null, info, token);
    }

    @Test
    public void shouldHaveWellKnownName() {
        assertThat(ssoTokenContext.getContextName()).isEqualTo("ssoToken");
    }

    @Test
    public void shouldContainInfoAndToken() {
        assertThat(ssoTokenContext.getInfo()).containsOnly(entry("valid", true),
                                                           entry("uid", "demo"),
                                                           entry("realm", "/"));
        assertThat(ssoTokenContext.getValue()).isEqualTo(TOKEN);
    }

    @Test
    public void shouldNotModifyOriginalInfo() {
        final JsonValue original = validSsoToken();
        ssoTokenContext = new SsoTokenContext(null, original, TOKEN);
        final JsonValue info = ssoTokenContext.asJsonValue().put("another", "entry");

        assertThat(info.get("another").asString()).isEqualTo("entry");
        assertThat(original.get("another").asString()).isNull();
    }

    private static JsonValue validSsoToken() {
        return json(object(field("valid", true),
                           field("uid", "demo"),
                           field("realm", "/")));
    }
}
