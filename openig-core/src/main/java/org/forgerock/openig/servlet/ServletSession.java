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

package org.forgerock.openig.servlet;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.forgerock.openig.http.Session;

/**
 * Exposes the session managed by the servlet container as an exchange session.
 * This implementation will get a servlet session if already allocated,
 * otherwise will not create one until an attempt is made to put an attribute in
 * it.
 */
public class ServletSession extends AbstractMap<String, Object> implements Session {

    /** The servlet request from which to get a servlet session object. */
    private final HttpServletRequest request;

    /** The servlet session object, if available. */
    private volatile HttpSession httpSession;

    /** The Map entrySet view of the session attributes. */
    private final Set<Entry<String, Object>> attributes = new AbstractSet<Entry<String, Object>>() {
        @Override
        public void clear() {
            ServletSession.this.clear();
        }

        @Override
        public boolean contains(final Object o) {
            return (o instanceof Entry)
                    && ServletSession.this.containsKey(((Entry<?, ?>) o).getKey());
        }

        @Override
        public boolean isEmpty() {
            return ServletSession.this.isEmpty();
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new Iterator<Entry<String, Object>>() {
                @SuppressWarnings("unchecked")
                final Enumeration<String> names = httpSession != null ? httpSession
                        .getAttributeNames() : null;

                @Override
                public boolean hasNext() {
                    return names != null && names.hasMoreElements();
                }

                @Override
                public Entry<String, Object> next() {
                    if (names == null) {
                        throw new NoSuchElementException();
                    }
                    final String name = names.nextElement();
                    return new SimpleEntry<String, Object>(name, httpSession.getAttribute(name)) {
                        private static final long serialVersionUID = -2957899005221454275L;

                        @Override
                        public Object setValue(final Object value) {
                            put(getKey(), value);
                            return super.setValue(value);
                        }
                    };
                }

                @Override
                public void remove() {
                    // Enumerations do not support concurrent removals.
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public boolean remove(final Object o) {
            return (o instanceof Entry)
                    && ServletSession.this.remove(((Entry<?, ?>) o).getKey()) != null;
        }

        @Override
        public int size() {
            return ServletSession.this.size();
        }
    };

    /**
     * Creates a new session object which manages sessions through the provided
     * servlet request object.
     *
     * @param request the servlet request object through which servlet sessions are
     * managed.
     */
    public ServletSession(final HttpServletRequest request) {
        this.request = request;
        this.httpSession = request.getSession(false); // get session if already allocated
    }

    @Override
    public void clear() {
        if (httpSession != null) {
            // Do in 2 steps to avoid CME.
            @SuppressWarnings("unchecked")
            final Enumeration<String> attributes = httpSession.getAttributeNames();
            final List<String> names = new ArrayList<String>();
            while (attributes.hasMoreElements()) {
                names.add(attributes.nextElement());
            }
            for (final String name : names) {
                httpSession.removeAttribute(name);
            }
        }
    }

    @Override
    public boolean containsKey(final Object key) {
        return get(key) != null;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return attributes;
    }

    @Override
    public Object get(final Object key) {
        Object value = null;
        if (key instanceof String && httpSession != null) {
            value = httpSession.getAttribute((String) key);
        }
        return value;
    }

    @Override
    public boolean isEmpty() {
        return httpSession == null || !httpSession.getAttributeNames().hasMoreElements();
    }

    @Override
    public synchronized Object put(final String key, final Object value) {
        final Object old = get(key);
        if (httpSession == null) {
            httpSession = request.getSession(true); // create session just-in-time
        }
        httpSession.setAttribute(key, value);
        return old;
    }

    @Override
    public Object remove(final Object key) {
        final Object old = get(key);
        if (key instanceof String && httpSession != null) {
            httpSession.removeAttribute((String) key);
        }
        return old;
    }

    @Override
    public int size() {
        int size = 0;
        if (httpSession != null) {
            final Enumeration<?> attributes = httpSession.getAttributeNames();
            while (attributes.hasMoreElements()) {
                attributes.nextElement();
                size++;
            }
        }
        return size;
    }

}
