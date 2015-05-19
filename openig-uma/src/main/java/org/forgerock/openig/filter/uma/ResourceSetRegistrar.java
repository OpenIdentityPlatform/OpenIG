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

public class ResourceSetRegistrar {

    private final Handler client;
    private final URI resourceServerEndpoint;

    public ResourceSetRegistrar(Handler client, URI resourceServerEndpoint) {
        this.client = client;
        this.resourceServerEndpoint = resourceServerEndpoint;
    }

    public String getResourceSetId() throws OAuth2TokenException {
        Request request = new Request();
        request.setMethod("GET");
        request.setUri(resourceServerEndpoint);
        // TODO Write the body


        Response response = client.handle(new Exchange(), request).getOrThrowUninterruptibly();

        if (isResponseEmpty(response)) {
            throw new OAuth2TokenException("Authorization Server did not return any Resource Sets");
        }

        JsonValue content = UmaUtils.asJson(response.getEntity());
        if (isOk(response)) {
            return content.get("resourceSetId").asString();
        }

        if (content.isDefined("error")) {
            String error = content.get("error").asString();
            String description = content.get("error_description").asString();
            throw new OAuth2TokenException(format("Authorization Server returned an error "
                    + "(error: %s, description: %s)", error, description));
        }

        return null;
    }

    private boolean isResponseEmpty(final Response response) {
        return (response == null) || (response.getEntity() == null);
    }

    private boolean isOk(final Response response) {
        return Status.OK.equals(response.getStatus());
    }
}
