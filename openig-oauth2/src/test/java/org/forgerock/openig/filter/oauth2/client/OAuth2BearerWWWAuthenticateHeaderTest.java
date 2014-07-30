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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.openig.filter.oauth2.client.OAuth2BearerWWWAuthenticateHeader.NAME;

import java.util.Arrays;
import java.util.List;

import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for the Bearer token WWW-Authenticate header class.
 */
@SuppressWarnings("javadoc")
public class OAuth2BearerWWWAuthenticateHeaderTest {

    @DataProvider
    private Object[][] validHeaders() {
        return new Object[][] {
            { null, null, null, null, null },
            { "Bearer", null, emptyList(), null, null },
            { "Bearer realm=\"example\"", "example", emptyList(), null, null },
            { "Bearer realm=\"example\", scope=\"openid email\"", "example",
                Arrays.asList("openid", "email"), null, null },
            {
                "Bearer realm=\"example\", scope=\"openid\", error=\"invalid_token\", "
                        + "error_description=\"The access token expired\"", "example",
                Arrays.asList("openid"), "invalid_token", "The access token expired" }, };
    }

    @Test(dataProvider = "validHeaders")
    public void testConstructFromMessage(final String header, String realm, List<String> scopes,
            String error, String errorDescription) {
        final Response response = new Response();
        response.headers.putSingle(NAME, header);
        OAuth2BearerWWWAuthenticateHeader parsed = new OAuth2BearerWWWAuthenticateHeader(response);
        assertEquals(parsed, header, realm, scopes, error, errorDescription);
    }

    @Test(dataProvider = "validHeaders")
    public void testConstructFromString(final String header, String realm, List<String> scopes,
            String error, String errorDescription) {
        OAuth2BearerWWWAuthenticateHeader parsed = new OAuth2BearerWWWAuthenticateHeader(header);
        assertEquals(parsed, header, realm, scopes, error, errorDescription);
    }

    @Test(dataProvider = "validHeaders")
    public void testFromMessage(final String header, String realm, List<String> scopes,
            String error, String errorDescription) {
        final Response response = new Response();
        response.headers.putSingle(NAME, header);
        OAuth2BearerWWWAuthenticateHeader parsed = new OAuth2BearerWWWAuthenticateHeader();
        parsed.fromMessage(response);
        assertEquals(parsed, header, realm, scopes, error, errorDescription);
    }

    @Test(dataProvider = "validHeaders")
    public void testFromString(final String header, String realm, List<String> scopes,
            String error, String errorDescription) {
        OAuth2BearerWWWAuthenticateHeader parsed = new OAuth2BearerWWWAuthenticateHeader();
        parsed.fromString(header);
        assertEquals(parsed, header, realm, scopes, error, errorDescription);
    }

    @Test
    public void testIs() {
        OAuth2BearerWWWAuthenticateHeader parsed =
                new OAuth2BearerWWWAuthenticateHeader(
                        "Bearer realm=\"example\", scope=\"openid\", error=\"invalid_token\", "
                                + "error_description=\"The access token expired\"");
        assertThat(parsed.getOAuth2Error().is(OAuth2Error.E_INVALID_TOKEN)).isTrue();
        assertThat(parsed.getOAuth2Error().is(OAuth2Error.E_ACCESS_DENIED)).isFalse();
    }

    private void assertEquals(OAuth2BearerWWWAuthenticateHeader parsed, final String header,
            String realm, List<String> scopes, String error, String errorDescription) {
        if (header == null) {
            assertThat(parsed.getOAuth2Error()).isNull();
            return;
        }
        assertThat(parsed.getOAuth2Error()).isNotNull();
        assertThat(parsed.getOAuth2Error().getRealm()).isEqualTo(realm);
        assertThat(parsed.getOAuth2Error().getScope()).isEqualTo(scopes);
        assertThat(parsed.getOAuth2Error().getError()).isEqualTo(error);
        assertThat(parsed.getOAuth2Error().getErrorDescription()).isEqualTo(errorDescription);
        assertThat(parsed.toString()).isEqualTo(header);
    }

}
