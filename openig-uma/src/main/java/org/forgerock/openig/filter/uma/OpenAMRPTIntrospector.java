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
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Entity;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;

public class OpenAMRptIntrospector implements RptIntrospector {

    private final Handler client;
    private final String tokenIntrospectionEndpoint;

    //For Demo
    private final String clientId;
    private final String clientSecret;

    public OpenAMRptIntrospector(Handler client, String tokenIntrospectionEndpoint,
            String clientId, String clientSecret) {
        this.client = client;
        this.tokenIntrospectionEndpoint = tokenIntrospectionEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public boolean introspect(String rpt) throws OAuth2TokenException {

        try {
            Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.setMethod("POST");
            exchange.request.setUri(new URI(tokenIntrospectionEndpoint));
            exchange.request.getHeaders().add("Content-Type", "application/x-www-form-urlencoded");

            // Append the access_token as a query parameter (automatically performs encoding)
            Form form = new Form();
            form.add("token", rpt);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.toRequestEntity(exchange.request);

            client.handle(exchange);

            if (isResponseEmpty(exchange)) {
                throw new OAuth2TokenException("Authorization Server did not return any RPT");
            }

            JsonValue content = asJson(exchange.response.getEntity());
            if (isOk(exchange.response)) {
                return content.get("active").asBoolean();
            }

            if (content.isDefined("error")) {
                String error = content.get("error").asString();
                String description = content.get("error_description").asString();
                throw new OAuth2TokenException(format("Authorization Server returned an error "
                        + "(error: %s, description: %s)", error, description));
            }

            return false;

        } catch (URISyntaxException e) {
            throw new OAuth2TokenException(
                    format("The introspection endpoint %s could not be accessed because it is a malformed URI",
                            tokenIntrospectionEndpoint), e);
        } catch (IOException e) {
            throw new OAuth2TokenException(format("Cannot introspect RPT from %s",
                    tokenIntrospectionEndpoint), e);
        } catch (HandlerException e) {
            throw new OAuth2TokenException(format("Could not handle call to introspection endpoint %s",
                    tokenIntrospectionEndpoint), e);
        }
    }

    private boolean isResponseEmpty(final Exchange exchange) {
        return (exchange.response == null) || (exchange.response.getEntity() == null);
    }

    private boolean isOk(final Response response) {
        return response.getStatus() == 200;
    }

    /**
     * Parse the response's content as a JSON structure.
     * @param entity stream response's content
     * @return {@link JsonValue} representing the JSON content
     * @throws OAuth2TokenException if there was some errors during parsing
     */
    private JsonValue asJson(final Entity entity) throws OAuth2TokenException {
        try {
            return new JsonValue(entity.getJson());
        } catch (IOException e) {
            // Do not use Entity.toString(), we probably don't want to fully output the content here
            throw new OAuth2TokenException("Cannot read response content as JSON", e);
        } finally {
            closeSilently(entity);
        }
    }
}
