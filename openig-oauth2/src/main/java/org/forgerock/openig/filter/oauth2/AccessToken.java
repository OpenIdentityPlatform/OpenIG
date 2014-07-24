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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import java.util.Set;

import org.forgerock.json.fluent.JsonValue;

/**
 * Represents an <a href="http://tools.ietf.org/html/rfc6749#section-1.4">OAuth2 Access Token</a>.
 */
public interface AccessToken {

    /**
     * Marker for never ending tokens.
     */
    final long NEVER_EXPIRES = Long.MAX_VALUE;

    /**
     * Returns the access token identifier issued from the authorization server.
     *
     * @return the access token identifier issued from the authorization server.
     */
    String getToken();

    /**
     * Returns the scopes associated to this token. Will return an empty Set if no scopes are associated with this
     * token.
     *
     * @return the scopes associated to this token.
     */
    Set<String> getScopes();

    /**
     * Returns the time (expressed as a timestamp in milliseconds since epoch) when this token will be expired. If the
     * {@link #NEVER_EXPIRES} constant is returned, this token is always considered as available.
     *
     * @return the time (expressed as a timestamp, in milliseconds since epoch) when this token will be expired.
     */
    long getExpiresAt();

    /**
     * Returns the raw JSON as returned by the {@literal tokeninfo} endpoint.
     *
     * @return the raw JSON as returned by the {@literal tokeninfo} endpoint.
     */
    JsonValue getRawInfo();
}
