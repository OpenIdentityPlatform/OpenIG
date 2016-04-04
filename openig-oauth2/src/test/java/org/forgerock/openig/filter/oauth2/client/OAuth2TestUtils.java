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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;

@SuppressWarnings("javadoc")
public final class OAuth2TestUtils {

    static final String AUTHORIZE_ENDPOINT = "/openam/oauth2/authorize";
    static final String TOKEN_ENDPOINT = "/openam/oauth2/access_token";
    static final String USER_INFO_ENDPOINT = "/openam/oauth2/userinfo";
    static final String REGISTRATION_ENDPOINT = "/openam/oauth2/connect/register";
    static final String WELLKNOWN_ENDPOINT = "/openam/oauth2/.well-known/openid-configuration";
    static final String ISSUER_URI = "http://www.example.com:8089";

    static ClientRegistration buildClientRegistration(final String clientName,
                                                      final Handler registrationHandler) throws Exception {
        return buildClientRegistration(clientName, registrationHandler, null);
    }

    static ClientRegistration buildClientRegistration(final String clientName,
                                                      final Handler registrationHandler,
                                                      final String issuerName) throws Exception {
        final JsonValue config = json(object(field("clientId", clientName),
                                             field("clientSecret", "password"),
                                             field("issuer", issuerName),
                                             field("scopes", array("openid"))));
        return new ClientRegistration(clientName,
                                      config,
                                      buildIssuerWithoutWellKnownEndpoint(issuerName),
                                      registrationHandler);
    }

    static Issuer buildIssuerWithoutWellKnownEndpoint(final String issuerName) {
        return buildIssuer(issuerName, false);
    }

    static Issuer buildIssuer(final String issuerName, final boolean addWellKnownEndpointToConfiguration) {
        final JsonValue configuration = json(object(
                field("authorizeEndpoint", ISSUER_URI + AUTHORIZE_ENDPOINT),
                field("registrationEndpoint", ISSUER_URI + REGISTRATION_ENDPOINT),
                field("tokenEndpoint", ISSUER_URI + TOKEN_ENDPOINT),
                field("userInfoEndpoint", ISSUER_URI + USER_INFO_ENDPOINT)));
        if (addWellKnownEndpointToConfiguration) {
            configuration.put("wellKnownEndpoint", ISSUER_URI + WELLKNOWN_ENDPOINT);
        }
        return new Issuer(issuerName != null ? issuerName : "myIssuer", configuration);
    }
}
