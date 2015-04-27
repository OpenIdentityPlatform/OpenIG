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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.http.protocol.Request;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OAuth2UtilsTest {

    @Test
    public void shouldBuildUriUsingOriginalExchangeUri() throws Exception {
        URI uri = OAuth2Utils.buildUri(buildExchange(), Expression.valueOf("/openid", String.class));
        assertThat(uri).isEqualTo(new URI("http://www.example.com/openid"));
    }

    @Test
    public void shouldNotChangeUriWhenExpressionIsAbsoluteUri() throws Exception {
        URI uri = OAuth2Utils.buildUri(buildExchange(),
                                       Expression.valueOf("http://accounts.google.com/openid/authorize", String.class));
        assertThat(uri).isEqualTo(new URI("http://accounts.google.com/openid/authorize"));
    }

    @Test(expectedExceptions = ResponseException.class)
    public void shouldFailIfComputedUriIsNotValid() throws Exception {
        // {} are invalid URI characters
        OAuth2Utils.buildUri(buildExchange(), Expression.valueOf("http://www.example.com/{boom}", String.class));
    }

    @Test
    public void shouldMatchesAgainstOriginalUri() throws Exception {
        assertThat(OAuth2Utils.matchesUri(buildExchange(), new URI("http://www.example.com"))).isTrue();
    }

    @Test
    public void shouldMatchesIgnoringQueryAndFragments() throws Exception {
        assertThat(OAuth2Utils.matchesUri(buildExchange(), new URI("http://www.example.com?p1=2#fragment"))).isTrue();
    }

    @Test
    public void shouldNotMatchesDifferentBaseUri() throws Exception {
        assertThat(OAuth2Utils.matchesUri(buildExchange(), new URI("http://openig.example.com"))).isFalse();
    }

    private Exchange buildExchange() throws URISyntaxException {
        Exchange exchange = new Exchange(new URI("http://www.example.com"));
        exchange.request = new Request();
        exchange.request.setUri("http://internal.company.com");
        return exchange;
    }
}
