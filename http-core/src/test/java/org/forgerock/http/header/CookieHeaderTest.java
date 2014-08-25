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
package org.forgerock.http.header;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.header.CookieHeader.NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.forgerock.http.Cookie;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
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
        final CookieHeader ch = CookieHeader.valueOf(CHEADER_1);
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertEquals(cookie.getVersion().intValue(), 1);
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(cookie.getPath(), "/example");
        assertEquals(cookie.getPort().size(), 2);
        assertEquals(cookie.getPort().get(0).intValue(), 42);
        assertEquals(cookie.getPort().get(1).intValue(), 13);
        assertEquals(ch.getName(), NAME);
    }

    @Test
    public void testCookieHeaderFromString2() {
        final CookieHeader ch = CookieHeader.valueOf(CHEADER_2);
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertEquals(cookie.getVersion().intValue(), 2);
        assertEquals(cookie.getValue(), "SAM_CARTER");
        assertEquals(cookie.getPath(), "/example");
        assertEquals(cookie.getPort().size(), 0);
        assertEquals(cookie.getDomain(), "example.com");
        assertEquals(ch.getName(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullVersion() {
        final CookieHeader ch = CookieHeader.valueOf("Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertNull(cookie.getVersion());
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(ch.getName(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsInvalidVersion() {
        final CookieHeader ch =
                CookieHeader
                        .valueOf("$Version=invalid; Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        final Cookie cookie = ch.getCookies().get(0);
        assertNull(cookie.getVersion());
        assertEquals(cookie.getValue(), "BAB_JENSEN");
        assertEquals(ch.getName(), NAME);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullString() {
        final CookieHeader ch = CookieHeader.valueOf((String) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test
    public void testCookieHeaderFromStringAllowsNullMessage() {
        final CookieHeader ch = CookieHeader.valueOf((Response) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test
    public void testCookieHeaderToString() {
        assertEquals(CookieHeader.valueOf(CHEADER_1).toString(), CHEADER_1);
    }


    @Test
    public void testCookieHeaderToStringInsertVersionWhenPathOrDomainArePresent() {
        CookieHeader ch = CookieHeader.valueOf("Customer=\"SAM_CARTER\";");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).doesNotContain("$Version=1;");

        ch = CookieHeader.valueOf("Customer=\"SAM_CARTER\"; $Path=\"/example\"");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).contains("$Version=1;");

        ch = CookieHeader.valueOf("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"");
        assertNull(ch.getCookies().get(0).getVersion());
        assertThat(ch.toString()).contains("$Version=1;");

        ch = CookieHeader.valueOf("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"; $Version=2");
        assertEquals(ch.getCookies().get(0).getVersion().intValue(), 2);
        assertThat(ch.toString()).doesNotContain("$Version=1;");
    }

    @Test
    public void testCookieHeaderToResponseMessage() {
        final Response response = new Response();
        assertNull(response.getHeaders().get("cookie"));
        assertNull(response.getHeaders().get("Customer"));
        final CookieHeader ch = CookieHeader.valueOf(CHEADER_1);
        response.getHeaders().putSingle(ch);
        assertNotNull(response.getHeaders().get("cookie"));
        assertNull(response.getHeaders().get("Customer"));
        assertEquals(response.getHeaders().get("cookie").get(0), CHEADER_1);
    }

    @Test
    public void testCookieHeaderToRequestMessage() {
        final Request request = new Request();
        assertNull(request.getCookies().get("cookie"));
        assertNull(request.getCookies().get("Customer"));
        final CookieHeader ch = CookieHeader.valueOf(CHEADER_1);
        request.getHeaders().putSingle(ch);
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
    public void testCookieHeaderClearCookies() {
        final CookieHeader ch = CookieHeader.valueOf(CHEADER_1);
        assertEquals(ch.getCookies().size(), 1);
        ch.getCookies().clear();
        assertEquals(ch.getCookies().size(), 0);
    }
}


