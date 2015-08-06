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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.openig.alias.ClassAliasResolver;
import org.forgerock.openig.filter.oauth2.client.AuthorizationRedirectHandler;
import org.forgerock.openig.filter.oauth2.client.ClientRegistration;
import org.forgerock.openig.filter.oauth2.client.DiscoveryFilter;
import org.forgerock.openig.filter.oauth2.client.ClientRegistrationFilter;
import org.forgerock.openig.filter.oauth2.client.Issuer;
import org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter;

/**
 * Register all the aliases supported by the {@literal openig-oauth2} module.
 */
public class OAuth2ClassAliasResolver implements ClassAliasResolver {
    private static final Map<String, Class<?>> ALIASES = new HashMap<>();

    static {
        ALIASES.put("ClientRegistration", ClientRegistration.class);
        ALIASES.put("DiscoveryFilter", DiscoveryFilter.class);
        ALIASES.put("ClientRegistrationFilter", ClientRegistrationFilter.class);
        ALIASES.put("Issuer", Issuer.class);
        ALIASES.put("OAuth2ClientFilter", OAuth2ClientFilter.class);
        ALIASES.put("OAuth2ResourceServerFilter", OAuth2ResourceServerFilter.class);
        ALIASES.put("OAuth2RSFilter", OAuth2ResourceServerFilter.class);
        ALIASES.put("AuthorizationRedirectHandler", AuthorizationRedirectHandler.class);
    }

    @Override
    public Class<?> resolve(final String alias) {
        return ALIASES.get(alias);
    }
}
