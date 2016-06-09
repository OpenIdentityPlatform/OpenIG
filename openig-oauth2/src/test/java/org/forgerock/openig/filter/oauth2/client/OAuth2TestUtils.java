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

import static java.util.Collections.singletonList;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.json.JsonValue;

/**
 * This class contains some utility methods used in OAuth2 unit tests only.
 */
@SuppressWarnings("javadoc")
public final class OAuth2TestUtils {

    static final String AUTHORIZE_ENDPOINT = "/openam/oauth2/authorize";
    static final String TOKEN_ENDPOINT = "/openam/oauth2/access_token";
    static final String USER_INFO_ENDPOINT = "/openam/oauth2/userinfo";
    static final String REGISTRATION_ENDPOINT = "/openam/oauth2/connect/register";
    static final String WELLKNOWN_ENDPOINT = "/openam/oauth2/.well-known/openid-configuration";
    static final String ISSUER_URI = "http://www.example.com:8089";
    static final String DEFAULT_SCOPE = "myScope";

    /** From the OIDC core spec. */
    static final String ID_TOKEN =
            // @formatter:off
                    "eyJhbGciOiJSUzI1NiIsImtpZCI6IjFlOWdkazcifQ.ewogImlzc"
                    + "yI6ICJodHRwOi8vc2VydmVyLmV4YW1wbGUuY29tIiwKICJzdWIiOiAiMjQ4Mjg5"
                    + "NzYxMDAxIiwKICJhdWQiOiAiczZCaGRSa3F0MyIsCiAibm9uY2UiOiAibi0wUzZ"
                    + "fV3pBMk1qIiwKICJleHAiOiAxMzExMjgxOTcwLAogImlhdCI6IDEzMTEyODA5Nz"
                    + "AKfQ.ggW8hZ1EuVLuxNuuIJKX_V8a_OMXzR0EHR9R6jgdqrOOF4daGU96Sr_P6q"
                    + "Jp6IcmD3HP99Obi1PRs-cwh3LO-p146waJ8IhehcwL7F09JdijmBqkvPeB2T9CJ"
                    + "NqeGpe-gccMg4vfKjkM8FcGvnzZUN4_KSP0aAp1tOJ1zZwgjxqGByKHiOtX7Tpd"
                    + "QyHE5lcMiKPXfEIQILVq0pc_E2DzL7emopWoaoZTF_m0_N0YzFC6g6EJbOEoRoS"
                    + "K5hoDalrcvRYLSrQAZZKflyuVCyixEoV9GfNQC3_osjzw2PAithfubEEBLuVVk4"
                    + "XUVrWOLrLl0nx7RkKU8NXNHq-rvKMzqg";
            // @formatter:on
    static final String REFRESH_TOKEN = "5dcc34f5-7617-4baf-b36b-77e1e8b8652b";

    static ClientRegistration buildClientRegistration(final String clientName,
                                                      final Handler registrationHandler) {
        return buildClientRegistration(clientName, registrationHandler, null);
    }

    static ClientRegistration buildClientRegistration(final String clientName,
                                                      final Handler registrationHandler,
                                                      final String issuerName) {
        return buildClientRegistrationWithScopes(clientName,
                                                 registrationHandler,
                                                 issuerName,
                                                 singletonList(DEFAULT_SCOPE));
    }

    static ClientRegistration buildClientRegistrationWithScopes(final String clientName,
                                                                final Handler registrationHandler,
                                                                final String issuerName,
                                                                final List<String> scopes) {
        final JsonValue config = json(object(field("clientId", clientName),
                                             field("clientSecret", "password"),
                                             field("issuer", issuerName),
                                             field("scopes", scopes)));
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

    static JsonValue buildAuthorizedOAuth2Session(final String clientRegistrationName, final String requestedUri) {
        return buildAuthorizedOAuth2Session(clientRegistrationName, requestedUri, singletonList(DEFAULT_SCOPE));
    }

    static JsonValue buildAuthorizedOAuth2Session(final String clientRegistrationName,
                                                  final String requestedUri,
                                                  final List<String> scopes) {
        return json(object(field("crn", clientRegistrationName),
                           field("arn", "af0ifjsldkj"),
                           field("ce", requestedUri),
                           field("s", scopes),
                           field("atr", object(field("access_token", ID_TOKEN),
                                               field("refresh_token", REFRESH_TOKEN),
                                               field("scope" , scopes),
                                               field("id_token", ID_TOKEN),
                                               field("token_type", "Bearer"),
                                               field("expires_in", 3600))),
                           field("ea", 1460018881)));
    }

    static SimpleMapSession newSession() {
        return new SimpleMapSession();
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException {
            // Nothing to do.
        }
    }
}
