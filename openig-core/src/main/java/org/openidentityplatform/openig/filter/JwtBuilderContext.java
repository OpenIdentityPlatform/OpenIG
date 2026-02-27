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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.filter;

import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

import java.util.Map;

import static org.forgerock.json.JsonValue.json;

public class JwtBuilderContext extends AbstractContext {

    private final String value;

    private final Map<String, Object> claims;

    private final JsonValue claimsAsJsonValue;

    JwtBuilderContext(Context parent, String value, Map<String, Object> claims) {
        super(parent, "jwtBuilder");
        this.value = value;
        this.claims = claims;
        this.claimsAsJsonValue = json(claims);
    }

    public String getValue() {
        return value;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public JsonValue getClaimsAsJsonValue() {
        return claimsAsJsonValue;
    }
}
