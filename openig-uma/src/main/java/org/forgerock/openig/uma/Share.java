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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.uma;

import java.util.UUID;
import java.util.regex.Pattern;

import org.forgerock.json.fluent.JsonValue;

/**
 * A {@link Share} is the data structure that maintains the mapping between the path of a shared resource
 * and the associated resource set.
 *
 * <p>The Protection Api Token (PAT) that binds the Resource Owner (RO), the Resource Server (RS) and the
 * Authorization Server (AS) is also stored herein because it is used when interacting with the permission
 * request endpoint (to issue a ticket) and the introspection endpoint (to decode the RPT).
 */
class Share {

    private final String id;
    private final ShareTemplate template;
    private final JsonValue resourceSet;
    private final Pattern pattern;
    private final String pat;

    Share(final ShareTemplate template,
          final JsonValue resourceSet,
          final Pattern pattern,
          final String accessToken) {
        this.id = UUID.randomUUID().toString();
        this.template = template;
        this.resourceSet = resourceSet;
        this.pattern = pattern;
        this.pat = accessToken;
    }

    public String getId() {
        return id;
    }

    public ShareTemplate getTemplate() {
        return template;
    }

    public String getResourceSetId() {
        return resourceSet.get("_id").asString();
    }

    public String getUserAccessPolicyUri() {
        return resourceSet.get("user_access_policy_uri").asString();
    }

    public String getPAT() {
        return pat;
    }

    public Pattern getPattern() {
        return pattern;
    }
}
