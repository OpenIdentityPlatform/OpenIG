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
 * Copyright © 2011-2014 ForgeRock AS.
 */
package org.forgerock.openig.header;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertTrue;
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
        assertTrue(ch.cookies.size() == 1);
        assertTrue(ch.cookies.get(0).version == 1);
        assertTrue(ch.cookies.get(0).value.equals("BAB_JENSEN"));
        assertTrue(ch.cookies.get(0).path.equals("/example"));
        assertTrue(ch.cookies.get(0).port.size() == 2);
        assertTrue(ch.cookies.get(0).port.get(0).toString().equals("42"));
        assertTrue(ch.cookies.get(0).port.get(1).toString().equals("13"));
        assertTrue(ch.getKey().equals(NAME));
    }

    @Test()
    public void cookieFromStringExample2() {
        final CookieHeader ch = new CookieHeader(cHeader2);
        assertTrue(ch.cookies.size() == 1);
        assertTrue(ch.cookies.get(0).version == 2);
        assertTrue(ch.cookies.get(0).value.equals("SAM_CARTER"));
        assertTrue(ch.cookies.get(0).path.equals("/example"));
        assertTrue(ch.cookies.get(0).port.size() == 0);
        assertTrue(ch.cookies.get(0).domain.equals("example.com"));
        assertTrue(ch.getKey().equals(NAME));
    }

    @Test()
    public void cookieFromStringAcceptsNullVersion() {
        final CookieHeader ch = new CookieHeader("Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertTrue(ch.cookies.size() == 1);
        assertTrue(ch.cookies.get(0).version == null);
        assertTrue(ch.cookies.get(0).value.equals("BAB_JENSEN"));
        assertTrue(ch.getKey().equals(NAME));
    }

    @Test()
    public void cookieFromStringDoesSupportInvalidVersion() {
        final CookieHeader ch = new CookieHeader("$Version=invalid; Customer=\"BAB_JENSEN\"; $Path=\"/example\"");
        assertTrue(ch.cookies.size() == 1);
        assertTrue(ch.cookies.get(0).version == null);
        assertTrue(ch.cookies.get(0).value.equals("BAB_JENSEN"));
        assertTrue(ch.getKey().equals(NAME));
    }

    @Test()
    public void getCookieFromStringAcceptsNullString() {
        final CookieHeader ch = new CookieHeader((String) null);
        assertTrue(ch.cookies.size() == 0);
    }

    @Test()
    public void getCookieFromStringAcceptsNullMessage() {
        final CookieHeader ch = new CookieHeader((Response) null);
        assertTrue(ch.cookies.size() == 0);
    }

    @Test()
    public void cookieToString() {
        assertThat(new CookieHeader(cHeader1).toString()).isEqualTo(cHeader1);
    }

    @Test()
    public void cookieToStringInsertVersionWhenPathOrDomainArePresent() {
        CookieHeader ch = new CookieHeader("Customer=\"SAM_CARTER\";");
        assertTrue(ch.cookies.get(0).version == null);
        assertThat(ch.toString()).doesNotContain("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Path=\"/example\"");
        assertTrue(ch.cookies.get(0).version == null);
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"");
        assertTrue(ch.cookies.get(0).version == null);
        assertThat(ch.toString()).contains("$Version=1;");

        ch = new CookieHeader("Customer=\"SAM_CARTER\"; $Domain=\"example.com\"; $Version=2");
        assertTrue(ch.cookies.get(0).version == 2);
        assertThat(ch.toString()).doesNotContain("$Version=1;");
    }

    @Test()
    public void cookieToResponseMessage() {
        final Response response = new Response();
        assertThat(response.headers.get("cookie")).isNull();
        assertThat(response.headers.get("Customer")).isNull();
        final CookieHeader ch = new CookieHeader(cHeader1);
        ch.toMessage(response);
        assertThat(response.headers.get("cookie")).isNotNull();
        assertThat(response.headers.get("Customer")).isNull();
        assertThat(response.headers.get("cookie").get(0).toString()).isEqualTo(cHeader1);
    }

    @Test()
    public void cookieToRequestMessage() {
        final Request request = new Request();
        assertThat(request.cookies.get("cookie")).isNull();
        assertThat(request.cookies.get("Customer")).isNull();
        final CookieHeader ch = new CookieHeader(cHeader1);
        ch.toMessage(request);
        assertThat(request.cookies).isNotNull();
        assertThat(request.cookies.get("cookie")).isNull();
        assertThat(request.cookies.get("Customer")).isNotNull();
        assertTrue(request.cookies.get("Customer").get(0).name.equals("Customer"));
        assertTrue(request.cookies.get("Customer").get(0).version == 1);
        assertTrue(request.cookies.get("Customer").get(0).port.size() == 2);
        assertTrue(request.cookies.get("Customer").get(0).value.equals("BAB_JENSEN"));
    }

    @Test()
    public void cookieHeaderEqualityIsTrue() {
        final CookieHeader ch = new CookieHeader(cHeader1);
        final CookieHeader ch2 = new CookieHeader(cHeader1);
        assertThat(ch).isEqualTo(ch2);
    }

    @Test()
    public void cookieHeaderEqualityIsFalse() {
        final CookieHeader ch = new CookieHeader(cHeader1);
        final CookieHeader ch2 = new CookieHeader(cHeader2);
        assertThat(ch).isNotEqualTo(ch2);
    }

    @Test()
    public void clearCookies() {
        final CookieHeader ch = new CookieHeader();
        assertTrue(ch.cookies.size() == 0);
        ch.fromString(cHeader1);
        assertTrue(ch.cookies.size() == 1);
        ch.cookies.clear();
        assertTrue(ch.cookies.size() == 0);
    }
}
