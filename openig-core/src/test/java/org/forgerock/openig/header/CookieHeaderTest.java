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
package org.forgerock.openig.header;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.header.CookieHeader.*;
import static org.testng.Assert.*;

import org.forgerock.openig.http.Cookie;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.Test;

/**
 * Unit tests for the cookie header class.
 * <p>
 * See <link>http://www.ietf.org/rfc/rfc2109.txt</link>
 * </p>
 */
@SuppressWarnings("javadoc")
public class CookieHeaderTest {

    private static final String CHEADER_1 = "$Version=1; Customer=\"BAB_JENSEN\"; $Path=\"/example\"; $Port=\"42,13\"";
    private static final String CHEADER_2 = "$Version=2; Customer=\"SAM_CARTER\"; $Path=\"/example\"; "
            + "$Domain=\"example.com\"";

    @Test
    public void testCookieHeaderFromString() {
        final CookieHeader ch = new CookieHeader(CHEADER_1);
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertEquals(cookie.getVersion().intValue(), 1);
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(cookie.getPath(), "/example");
        assertEquals(cookie.getPort().size(), 2);
        assertEquals(cookie.getPort().get(0).intValue(), 42);
        assertEquals(cookie.getPort().get(1).intValue(), 13);
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testCookieHeaderFromString2() {
        final CookieHeader ch = new CookieHeader(CHEADER_2);
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertEquals(cookie.getVersion().intValue(), 2);
        assertEquals(cookie.getValue(), "SAM_CARTER");
        assertEquals(cookie.getPath(), "/example");
        assertEquals(cookie.getPort().size(), 0);
        assertEquals(cookie.getDomain(), "example.com");
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullVersion() {
        final CookieHeader ch = new CookieHeader("Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertNull(cookie.getVersion());
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsInvalidVersion() {
        final CookieHeader ch = new CookieHeader("$Version=invalid; Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertNull(cookie.getVersion());
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullString() {
        final CookieHeader ch = new CookieHeader((String) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullMessage() {
        final CookieHeader ch = new CookieHeader((Response) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test
    public void testCookieHeaderToString() {
        assertEquals(new CookieHeader(CHEADER_1).toString(), CHEADER_1);
    }


    @Test
    public void testCookieHeaderToStringInsertVersionWhenPathOrDomainArePresent() {
        CookieHeader ch = new CookieHeader("Customer=\"SAM_CARTER\";");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).doesNotContain("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Path=\"/example\"");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"; $Version=2");
        assertEquals(ch.getCookies().get(0).getVersion().intValue(), 2);
        assertThat(ch.toString()).doesNotContain("$Version=1;");
    }

    @Test
    public void testCookieHeaderToResponseMessage() {
        final Response response = new Response();
        assertNull(response.getHeaders().get("cookie"));
        assertNull(response.getHeaders().get("Customer"));
        final CookieHeader ch = new CookieHeader(CHEADER_1);
        ch.toMessage(response);
        assertNotNull(response.getHeaders().get("cookie"));
        assertNull(response.getHeaders().get("Customer"));
        assertEquals(response.getHeaders().get("cookie").get(0), CHEADER_1);
    }

    @Test
    public void testCookieHeaderToRequestMessage() {
        final Request request = new Request();
        assertNull(request.getCookies().get("cookie"));
        assertNull(request.getCookies().get("Customer"));
        final CookieHeader ch = new CookieHeader(CHEADER_1);
        ch.toMessage(request);
        assertNotNull(request.getCookies());
        assertNull(request.getCookies().get("cookie"));
        assertNotNull(request.getCookies().get("Customer"));

        final Cookie cookie = request.getCookies().get("Customer").get(0);
        assertEquals(cookie.getName(), "Customer");
        assertEquals(cookie.getVersion().intValue(), 1);
        assertEquals(cookie.getPort().size(), 2);
        assertEquals(cookie.getValue(), "BAB_JENSEN");
    }

    @Test
    public void testCookieHeaderEqualityIsTrue() {
        final CookieHeader ch = new CookieHeader(CHEADER_1);
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, CHEADER_1);

        final CookieHeader ch2 = new CookieHeader();
        ch2.fromMessage(response);
        assertEquals(ch2.getCookies(), ch.getCookies());
        assertEquals(ch, ch2);
    }

    @Test
    public void testCookieHeaderEqualityIsFalse() {
        final CookieHeader ch = new CookieHeader(CHEADER_1);
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, CHEADER_2);

        final CookieHeader ch2 = new CookieHeader();
        ch2.fromMessage(response);
        assertThat(ch2.getCookies()).isNotEqualTo(ch.getCookies());
        assertThat(ch).isNotEqualTo(ch2);
    }

    @Test
    public void testCookieHeaderClearCookies() {
        final CookieHeader ch = new CookieHeader();
        assertEquals(ch.getCookies().size(), 0);
        ch.fromString(CHEADER_1);
        assertEquals(ch.getCookies().size(), 1);
        ch.getCookies().clear();
        assertEquals(ch.getCookies().size(), 0);
    }
}


