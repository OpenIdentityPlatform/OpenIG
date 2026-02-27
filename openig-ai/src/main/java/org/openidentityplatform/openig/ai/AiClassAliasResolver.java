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

package org.openidentityplatform.openig.ai;

import org.forgerock.openig.alias.ClassAliasResolver;
import org.openidentityplatform.openig.ai.filter.MCPServerFeaturesFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Register all the aliases supported by the {@literal openig-ai2} module.
 */
public class AiClassAliasResolver implements ClassAliasResolver {
    private static final Map<String, Class<?>> ALIASES = new HashMap<>();

    static {
        ALIASES.put("MCPServerFeaturesFilter", MCPServerFeaturesFilter.class);
    }

    @Override
    public Class<?> resolve(final String alias) {
        return ALIASES.get(alias);
    }
}
