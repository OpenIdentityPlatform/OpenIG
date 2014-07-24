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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.resolver;

import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.AccessTokenResolver;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.util.time.TimeService;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * An {@link OpenAmAccessTokenResolver} knows how to resolve a given token identifier against an OpenAm instance.
 */
public class OpenAmAccessTokenResolver implements AccessTokenResolver {

    private final Handler client;
    private final String tokenInfoEndpoint;
    private final OpenAmAccessToken.Builder builder;

    /**
     * Creates a new {@link OpenAmAccessTokenResolver} configured to access the given {@literal /oauth2/tokeninfo}
     * OpenAm endpoint.
     *
     * @param client
     *         Http client handler used to perform the request
     * @param time
     *         Time service used to compute the token expiration time
     * @param tokenInfoEndpoint
     *         full URL of the {@literal /oauth2/tokeninfo} endpoint
     */
    public OpenAmAccessTokenResolver(final Handler client,
                                     final TimeService time,
                                     final String tokenInfoEndpoint) {
        this(client, new OpenAmAccessToken.Builder(time), tokenInfoEndpoint);
    }

    /**
     * Creates a new {@link OpenAmAccessTokenResolver} configured to access the given {@literal /oauth2/tokeninfo}
     * OpenAm endpoint.
     *
     * @param client
     *         Http client handler used to perform the request
     * @param builder
     *         AccessToken builder
     * @param tokenInfoEndpoint
     *         full URL of the {@literal /oauth2/tokeninfo} endpoint
     */
    public OpenAmAccessTokenResolver(final Handler client,
                                     final OpenAmAccessToken.Builder builder,
                                     final String tokenInfoEndpoint) {
        this.client = client;
        this.builder = builder;
        this.tokenInfoEndpoint = tokenInfoEndpoint;
    }


    @Override
    public AccessToken resolve(final String token) throws OAuth2TokenException {
        try {
            Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.method = "GET";
            exchange.request.uri = new URI(tokenInfoEndpoint);

            // Append the access_token as a query parameter (automatically performs encoding)
            Form form = new Form();
            form.add("access_token", token);
            form.toRequestQuery(exchange.request);

            // Call the client handler
            client.handle(exchange);

            if (isResponseEmpty(exchange)) {
                throw new OAuth2TokenException("empty", "Authorization Server did not returned an AccessToken");
            }

            JsonValue content = asJson(exchange.response.entity);
            if (isOk(exchange.response)) {
                return builder.build(content);
            }

            if (content.isDefined("error")) {
                String error = content.get("error").asString();
                String description = content.get("error_description").asString();
                throw new OAuth2TokenException(error, description);
            }

            throw new OAuth2TokenException("unknown",
                                           "AccessToken returned by the AuthorizationServer has a problem");
        } catch (URISyntaxException e) {
            throw new OAuth2TokenException("uri", "token_info endpoint URI is malformed", e);
        } catch (IOException e) {
            throw new OAuth2TokenException("io", "Error during the Http resource invocation", e);
        } catch (HandlerException e) {
            throw new OAuth2TokenException("handler", "Could not handle call to token_info endpoint", e);
        }
    }

    private boolean isResponseEmpty(final Exchange exchange) {
        return (exchange.response == null) || (exchange.response.entity == null);
    }

    private boolean isOk(final Response response) {
        return response.status == 200;
    }

    /**
     * Parse the response's content as a JSON structure.
     * @param stream response's content
     * @return {@link JsonValue} representing the JSON content
     * @throws OAuth2TokenException if there was some errors during parsing
     */
    private JsonValue asJson(final InputStream stream) throws OAuth2TokenException {
        JSONParser parser = new JSONParser();
        Object parsed = null;
        try {
            parsed = parser.parse(new BufferedReader(new InputStreamReader(stream)));
        } catch (IOException e) {
            throw new OAuth2TokenException("io", "Cannot read response content", e);
        } catch (ParseException e) {
            throw new OAuth2TokenException("parse", "Cannot parse response content as JSON", e);
        } finally {
            closeSilently(stream);
        }
        return new JsonValue(parsed);
    }


}
