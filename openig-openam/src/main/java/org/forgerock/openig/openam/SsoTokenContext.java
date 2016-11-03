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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static org.forgerock.util.Reject.checkNotNull;

import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * An {@link SsoTokenContext} could be used to store and retrieve an token.
 * <p>The {@link SingleSignOnFilter} uses this context to store the token and
 * its validation information.
 */
public class SsoTokenContext extends AbstractContext {

    private final JsonValue info;
    private final String token;

    /**
     * Creates a new {@link SsoTokenContext} context with the provided token
     * and additional validation information such as 'realm', 'uid' and 'authModule'.
     *
     * @param parent
     *         The parent context.
     * @param info
     *         The validation information associated with this context to store, not {@code null}.
     * @param token
     *         The token associated with this context to store, not {@code null}.
     */
    public SsoTokenContext(final Context parent, final JsonValue info, final String token) {
        super(parent, "ssoToken");
        this.info = checkNotNull(info);
        this.token = checkNotNull(token);
    }

    /**
     * Returns the info associated with this context.
     *
     * @return the info JSON as a map.
     */
    public Map<String, Object> getInfo() {
        return info.copy().asMap();
    }

    /**
     * Returns the info associated with this context as a {@link JsonValue}.
     *
     * @return the info associated with this context as a {@link JsonValue}.
     */
    public JsonValue asJsonValue() {
        return info.copy();
    }

    /**
     * Returns the token associated with this SsoTokenContext context.
     *
     * @return the token associated with this SsoTokenContext context.
     */
    public String getValue() {
        return token;
    }
}
