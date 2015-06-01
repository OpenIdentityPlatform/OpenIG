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
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.http.Exchange;

public class OpenAMRPTIntrospector implements RPTIntrospector {

    private final Handler client;
    private final URI tokenIntrospectionEndpoint;

    //For Demo
    private final String clientId;
    private final String clientSecret;

    public OpenAMRPTIntrospector(Handler client,
                                 URI tokenIntrospectionEndpoint,
                                 String clientId,
                                 String clientSecret) {
        this.client = client;
        this.tokenIntrospectionEndpoint = tokenIntrospectionEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public boolean introspect(String rpt) throws OAuth2TokenException {

        Request request = new Request();
        request.setMethod("POST");
        request.setUri(tokenIntrospectionEndpoint);
        request.getHeaders().add("Content-Type", "application/x-www-form-urlencoded");

        // Append the access_token as a query parameter (automatically performs encoding)
        Form form = new Form();
        form.add("token", rpt);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.toRequestEntity(request);

        Response response = client.handle(new Exchange(), request).getOrThrowUninterruptibly();

        if (isResponseEmpty(response)) {
            throw new OAuth2TokenException("Authorization Server did not return any RPT");
        }

        JsonValue content = UmaUtils.asJson(response.getEntity());
        if (isOk(response)) {
            return content.get("active").asBoolean();
        }

        if (content.isDefined("error")) {
            String error = content.get("error").asString();
            String description = content.get("error_description").asString();
            throw new OAuth2TokenException(format("Authorization Server returned an error "
                    + "(error: %s, description: %s)", error, description));
        }

        return false;
    }

    private boolean isResponseEmpty(final Response response) {
        return (response == null) || (response.getEntity() == null);
    }

    private boolean isOk(final Response response) {
        return Status.OK.equals(response.getStatus());
    }
}
