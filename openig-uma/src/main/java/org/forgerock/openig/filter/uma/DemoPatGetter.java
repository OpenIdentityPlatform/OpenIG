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

package org.forgerock.openig.filter.uma;

import static java.lang.String.format;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.http.Exchange;

public class DemoPatGetter {

    private final Handler client;
    private final URI accessTokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;

    public DemoPatGetter(Handler client,
                         URI accessTokenEndpoint,
                         String clientId,
                         String clientSecret,
                         String username,
                         String password) {
        this.client = client;
        this.accessTokenEndpoint = accessTokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
    }

    public String getPAT() throws OAuth2TokenException {
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(accessTokenEndpoint);
        request.getHeaders().add("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity("client_id=" + clientId + "&client_secret=" + clientSecret
                + "&grant_type=password&scope=uma_protection&username=" + username  + "&password=" + password);

        Response response = client.handle(new Exchange(), request).getOrThrowUninterruptibly();

        JsonValue content = UmaUtils.asJson(response.getEntity());
        if (isOk(response)) {
            return content.get("access_token").asString();
        }

        if (content.isDefined("error")) {
            String error = content.get("error").asString();
            String description = content.get("error_description").asString();
            throw new OAuth2TokenException(format("Authorization Server returned an error "
                    + "(error: %s, description: %s)", error, description));
        }

        throw new OAuth2TokenException(format("Can't obtain PAT from Authorization Server (%s)",
                                              response.getStatus()));
    }

    private boolean isOk(final Response response) {
        return Status.OK.equals(response.getStatus());
    }
}
