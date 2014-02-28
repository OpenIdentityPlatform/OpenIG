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
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.http;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.forgerock.openig.header.CookieHeader;
import org.forgerock.openig.util.UnmodifiableCollection;

/**
 * Exposes incoming request cookies.
 */
public class RequestCookies extends AbstractMap<String, List<Cookie>> implements
        Map<String, List<Cookie>>, UnmodifiableCollection {
    // TODO: maybe some intelligent caching so each call to get doesn't re-parse the cookies

    /** The request to read cookies from. */
    private final Request request;

    /**
     * Constructs a new request cookies object that reads cookies from the
     * specified request.
     * 
     * @param request
     *            the request to read cookies from.
     */
    public RequestCookies(final Request request) {
        this.request = request;
    }

    @Override
    public boolean containsKey(final Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(final Object value) {
        return cookies().containsValue(value);
    }

    @Override
    public Set<Entry<String, List<Cookie>>> entrySet() {
        return cookies().entrySet();
    }

    @Override
    public List<Cookie> get(final Object key) {
        final ArrayList<Cookie> list = new ArrayList<Cookie>();
        if (key instanceof String) {
            final String s = (String) key;
            for (final Cookie cookie : new CookieHeader(request).cookies) {
                if (s.equalsIgnoreCase(cookie.name)) {
                    list.add(cookie);
                }
            }
        }
        return list.size() > 0 ? list : null;
    }

    @Override
    public boolean isEmpty() {
        return new CookieHeader(request).cookies.isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return cookies().keySet();
    }

    @Override
    public int size() {
        return new CookieHeader(request).cookies.size();
    }

    @Override
    public String toString() {
        return cookies().toString();
    }

    @Override
    public Collection<List<Cookie>> values() {
        return cookies().values();
    }

    private Map<String, List<Cookie>> cookies() {
        final Map<String, List<Cookie>> cookies =
                new TreeMap<String, List<Cookie>>(String.CASE_INSENSITIVE_ORDER);
        for (final Cookie cookie : new CookieHeader(request).cookies) {
            List<Cookie> list = cookies.get(cookie.name);
            if (list == null) {
                cookies.put(cookie.name, list = new ArrayList<Cookie>(1));
            }
            list.add(cookie);
        }
        return cookies;
    }

}
