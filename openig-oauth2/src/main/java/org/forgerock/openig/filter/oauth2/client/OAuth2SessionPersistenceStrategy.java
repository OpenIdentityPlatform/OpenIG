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

import org.forgerock.openig.http.Exchange;

/**
 * A strategy whose responsibility is to persist the user's OAuth2 session state
 * information between successive HTTP requests. Two options are provided:
 * <ul>
 * <li>Servlet Session: simple and secure, but less RESTful and therefore less
 * scalable. In particular, Servlet Sessions are local to a container unless
 * some kind of distributed session cache is employed
 * <li>JWT Cookie: RESTful approach that pushes the responsibility of
 * maintaining session state to the user agent. There are a couple of minor
 * drawbacks to this approach: 1) increased vulnerability due to sensitive
 * information being stored in the user agent, and 2) increased bandwidth
 * because the JWT cookie is much larger than the session cookie.
 * </ul>
 */
interface OAuth2SessionPersistenceStrategy {

    /**
     * A strategy which persists the OAuth2 session state to the Servlet's
     * Session.
     */
    static final OAuth2SessionPersistenceStrategy SESSION = new OAuth2SessionPersistenceStrategy() {

        @Override
        public OAuth2Session load(final String key, final Exchange exchange) {
            return (OAuth2Session) exchange.session.get(key);
        }

        @Override
        public void remove(final String key, final Exchange exchange) {
            exchange.session.remove(key);

        }

        @Override
        public void save(final String key, final Exchange exchange, final OAuth2Session session) {
            exchange.session.put(key, session);
        }
    };

    /**
     * Loads the OAuth2 session from the provided exchange, returning
     * {@code null} if there is no session.
     *
     * @param key
     *            The session ID.
     * @param exchange
     *            The exchange.
     * @return The OAuth2 session or {@code null} if there is no session.
     */
    OAuth2Session load(String key, Exchange exchange);

    /**
     * Removes the OAuth2 session from the provided exchange. The exchange will
     * already contain a response.
     *
     * @param key
     *            The session ID.
     * @param exchange
     *            The exchange which already has a response prepared.
     */
    void remove(String key, Exchange exchange);

    /**
     * Saves the OAuth2 session to the provided exchange. The exchange will
     * already contain a response.
     *
     * @param key
     *            The session ID.
     * @param exchange
     *            The exchange which already has a response prepared.
     * @param session
     *            The OAuth2 session, which may be {@code null} indicating that
     *            any session should be removed.
     */
    void save(String key, Exchange exchange, OAuth2Session session);
}
