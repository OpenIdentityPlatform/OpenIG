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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http.header;

import static org.forgerock.http.header.HeaderUtil.parseMultiValuedHeader;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.http.Cookie;
import org.forgerock.http.Header;
import org.forgerock.http.Message;

/**
 * Processes the <strong>{@code Cookie}</strong> request message header. For
 * more information, see the original <a href=
 * "http://web.archive.org/web/20070805052634/http://wp.netscape.com/newsref/std/cookie_spec.html"
 * > Netscape specification</a>, <a
 * href="http://www.ietf.org/rfc/rfc2109.txt">RFC 2109</a> and <a
 * href="http://www.ietf.org/rfc/rfc2965.txt">RFC 2965</a>.
 * <p>
 * Note: This implementation is designed to be forgiving when parsing malformed
 * cookies.
 */
public class CookieHeader implements Header {
    private static CookieHeader valueOf(final List<String> values) {
        List<Cookie> cookies = new ArrayList<Cookie>(values.size());
        Integer version = null;
        Cookie cookie = new Cookie();
        for (String s1 : values) {
            for (String s2 : HeaderUtil.split(s1, ';')) {
                String[] nvp = HeaderUtil.parseParameter(s2);
                if (nvp[0].length() > 0 && nvp[0].charAt(0) != '$') {
                    if (cookie.getName() != null) {
                        // existing cookie was being parsed
                        cookies.add(cookie);
                    }
                    cookie = new Cookie();
                    // inherit previous parsed version
                    cookie.setVersion(version);
                    cookie.setName(nvp[0]);
                    cookie.setValue(nvp[1]);
                } else if ("$Version".equalsIgnoreCase(nvp[0])) {
                    cookie.setVersion(version = parseInteger(nvp[1]));
                } else if ("$Path".equalsIgnoreCase(nvp[0])) {
                    cookie.setPath(nvp[1]);
                } else if ("$Domain".equalsIgnoreCase(nvp[0])) {
                    cookie.setDomain(nvp[1]);
                } else if ("$Port".equalsIgnoreCase(nvp[0])) {
                    cookie.getPort().clear();
                    parsePorts(cookie.getPort(), nvp[1]);
                }
            }
        }
        if (cookie.getName() != null) {
            // last cookie being parsed
            cookies.add(cookie);
        }
        return new CookieHeader(cookies);
    }

    private static void parsePorts(List<Integer> list, String s) {
        for (String port : s.split(",")) {
            Integer p = parseInteger(port);
            if (p != null) {
                list.add(p);
            }
        }
    }

    private static Integer parseInteger(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Constructs a new header, initialized from the specified message.
     *
     * @param message
     *            The message to initialize the header from.
     * @return The parsed header.
     */
    public static CookieHeader valueOf(final Message message) {
        return valueOf(parseMultiValuedHeader(message, NAME));
    }

    /**
     * Constructs a new header, initialized from the specified string value.
     *
     * @param string
     *            The value to initialize the header from.
     * @return The parsed header.
     */
    public static CookieHeader valueOf(final String string) {
        return valueOf(parseMultiValuedHeader(string));
    }

    /** The name of this header. */
    public static final String NAME = "Cookie";

    /** Request message cookies. */
    private final List<Cookie> cookies;

    /**
     * Constructs a new empty header.
     */
    public CookieHeader() {
        this(new ArrayList<Cookie>(1));
    }

    /**
     * Constructs a new header with the provided cookies.
     *
     * @param cookies
     *            The cookies.
     */
    public CookieHeader(List<Cookie> cookies) {
        this.cookies = cookies;
    }

    /**
     * Returns the cookies' request list.
     *
     * @return The cookies' request list.
     */
    public List<Cookie> getCookies() {
        return cookies;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        boolean quoted = false;
        Integer version = null;
        for (Cookie cookie : cookies) {
            if (cookie.getVersion() != null && (version == null || cookie.getVersion() > version)) {
                version = cookie.getVersion();
            } else if (version == null && (cookie.getPath() != null || cookie.getDomain() != null)) {
                // presence of extended fields makes it version 1 at minimum
                version = 1;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (version != null) {
            sb.append("$Version=").append(version.toString());
            quoted = true;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName() != null) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(cookie.getName()).append('=');
                sb.append(quoted ? HeaderUtil.quote(cookie.getValue()) : cookie.getValue());
                if (cookie.getPath() != null) {
                    sb.append("; $Path=").append(HeaderUtil.quote(cookie.getPath()));
                }
                if (cookie.getDomain() != null) {
                    sb.append("; $Domain=").append(HeaderUtil.quote(cookie.getDomain()));
                }
                if (cookie.getPort().size() > 0) {
                    sb.append("; $Port=").append(HeaderUtil.quote(portList(cookie.getPort())));
                }
            }
        }
        // return null if empty
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String portList(List<Integer> ports) {
        StringBuilder sb = new StringBuilder();
        for (Integer port : ports) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(port.toString());
        }
        return sb.toString();
    }
}
