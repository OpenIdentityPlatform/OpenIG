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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.header;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.*;
import static org.forgerock.openig.header.CookieHeader.NAME;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.Test;

/**
 * Unit tests for the cookie header class.
 * <p>
 * See <link>http://www.ietf.org/rfc/rfc2109.txt</link>
 * </p>
 */
public class CookieHeaderTest {

    private static String cHeader1 = "$Version=1; Customer=\"BAB_JENSEN\"; $Path=\"/example\"; $Port=\"42,13\"";
    private static String cHeader2 = "$Version=2; Customer=\"SAM_CARTER\"; $Path=\"/example\"; $Domain=\"example.com\"";

    @Test()
    public void cookieFromStringExample1() {
        final CookieHeader ch = new CookieHeader(cHeader1);
        assertEquals(ch.getCookies().size(), 1);
        assertEquals(ch.getCookies().get(0).version.intValue(), 1);
        assertEquals(ch.getCookies().get(0).value, "BAB_JENSEN");
        assertEquals(ch.getCookies().get(0).path, "/example");
        assertEquals(ch.getCookies().get(0).port.size(), 2);
        assertEquals(ch.getCookies().get(0).port.get(0).intValue(), 42);
        assertEquals(ch.getCookies().get(0).port.get(1).intValue(), 13);
        assertEquals(ch.getKey(), NAME);
    }

    @Test()
    public void cookieFromStringExample2() {
        final CookieHeader ch = new CookieHeader(cHeader2);
        assertEquals(ch.getCookies().size(), 1);
        assertEquals(ch.getCookies().get(0).version.intValue(), 2);
        assertEquals(ch.getCookies().get(0).value, "SAM_CARTER");
        assertEquals(ch.getCookies().get(0).path, "/example");
        assertEquals(ch.getCookies().get(0).port.size(), 0);
        assertEquals(ch.getCookies().get(0).domain, "example.com");
        assertEquals(ch.getKey(), NAME);
    }

    @Test()
    public void cookieFromStringAllowsNullVersion() {
        final CookieHeader ch = new CookieHeader("Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        assertNull(ch.getCookies().get(0).version);
        assertEquals(ch.getCookies().get(0).value, "BAB_JENSEN");
        assertEquals(ch.getKey(), NAME);
    }

    @Test()
    public void cookieFromStringAllowsInvalidVersion() {
        final CookieHeader ch = new CookieHeader("$Version=invalid; Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertEquals(ch.getCookies().size(), 1);
        assertNull(ch.getCookies().get(0).version);
        assertTrue(ch.getCookies().get(0).value.equals("BAB_JENSEN"));
        assertEquals(ch.getKey(), NAME);
    }

    @Test()
    public void getCookieFromStringAllowsNullString() {
        final CookieHeader ch = new CookieHeader((String) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test()
    public void getCookieFromStringAllowsNullMessage() {
        final CookieHeader ch = new CookieHeader((Response) null);
        assertEquals(ch.getCookies().size(), 0);
    }

    @Test()
    public void cookieToString() {
        assertEquals(new CookieHeader(cHeader1).toString(), cHeader1);
    }

    @Test()
    public void cookieToStringInsertVersionWhenPathOrDomainArePresent() {
        CookieHeader ch = new CookieHeader("Customer=\"SAM_CARTER\";");
        assertNull(ch.getCookies().get(0).version);
        assertThat(ch.toString()).doesNotContain("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Path=\"/example\"");
        assertNull(ch.getCookies().get(0).version);
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"");
        assertNull(ch.getCookies().get(0).version);
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"; $Version=2");
        assertTrue(ch.getCookies().get(0).version == 2);
        assertThat(ch.toString()).doesNotContain("$Version=1;");
    }

    @Test()
    public void cookieToResponseMessage() {
        final Response response = new Response();
        assertNull(response.headers.get("cookie"));
        assertNull(response.headers.get("Customer"));
        final CookieHeader ch = new CookieHeader(cHeader1);
        ch.toMessage(response);
        assertNotNull(response.headers.get("cookie"));
        assertNull(response.headers.get("Customer"));
        assertEquals(response.headers.get("cookie").get(0), cHeader1);
    }

    @Test()
    public void cookieToRequestMessage() {
        final Request request = new Request();
        assertNull(request.cookies.get("cookie"));
        assertNull(request.cookies.get("Customer"));
        final CookieHeader ch = new CookieHeader(cHeader1);
        ch.toMessage(request);
        assertNotNull(request.cookies);
        assertNull(request.cookies.get("cookie"));
        assertNotNull(request.cookies.get("Customer"));
        assertEquals(request.cookies.get("Customer").get(0).name, "Customer");
        assertEquals(request.cookies.get("Customer").get(0).version.intValue(), 1);
        assertEquals(request.cookies.get("Customer").get(0).port.size(), 2);
        assertEquals(request.cookies.get("Customer").get(0).value, "BAB_JENSEN");
    }

    @Test()
    public void cookieHeaderEqualityIsTrue() {
        final CookieHeader ch = new CookieHeader(cHeader1);
        final Response response = new Response();
        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, cHeader1);

        final CookieHeader ch2 = new CookieHeader();
        ch2.fromMessage(response);
        assertEquals(ch2.getCookies(), ch.getCookies());
        assertEquals(ch, ch2);
    }

    @Test()
    public void cookieHeaderEqualityIsFalse() {
        final CookieHeader ch = new CookieHeader(cHeader1);
        final Response response = new Response();
        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, cHeader2);

        final CookieHeader ch2 = new CookieHeader();
        ch2.fromMessage(response);
        assertFalse(ch2.getCookies().equals(ch.getCookies()));
        assertThat(ch).isNotEqualTo(ch2);
    }

    @Test()
    public void clearCookies() {
        final CookieHeader ch = new CookieHeader();
        assertEquals(ch.getCookies().size(), 0);
        ch.fromString(cHeader1);
        assertEquals(ch.getCookies().size(), 1);
        ch.getCookies().clear();
        assertEquals(ch.getCookies().size(), 0);
    }
}
