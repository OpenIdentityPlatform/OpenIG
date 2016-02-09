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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.resolver;

import static java.lang.String.format;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.authz.modules.oauth2.AccessToken;
import org.forgerock.authz.modules.oauth2.AccessTokenResolver;
import org.forgerock.authz.modules.oauth2.AccessTokenException;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Entity;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.http.Responses;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.TimeService;

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
    public Promise<AccessToken, AccessTokenException> resolve(Context context, final String token) {
        try {
            Request request = new Request();
            request.setMethod("GET");
            request.setUri(new URI(tokenInfoEndpoint));

            // Append the access_token as a query parameter (automatically performs encoding)
            Form form = new Form();
            form.add("access_token", token);
            form.toRequestQuery(request);

            // Call the client handler
            return client.handle(context, request)
                         .then(onResult(), Responses.<AccessToken, AccessTokenException>noopExceptionFunction());
        } catch (URISyntaxException e) {
            return Promises.newExceptionPromise(new AccessTokenException(
                    format("The token_info endpoint %s could not be accessed because it is a malformed URI",
                           tokenInfoEndpoint),
                    e));
        }
    }

    private Function<Response, AccessToken, AccessTokenException> onResult() {
        return new Function<Response, AccessToken, AccessTokenException>() {
            @Override
            public AccessToken apply(Response response) throws AccessTokenException {
                if (isResponseEmpty(response)) {
                    throw new AccessTokenException("Authorization Server did not return any AccessToken");
                }
                JsonValue content = asJson(response.getEntity());
                if (isOk(response)) {
                    return builder.build(content);
                }

                if (content.isDefined("error")) {
                    String error = content.get("error").asString();
                    String description = content.get("error_description").asString();
                    throw new AccessTokenException(format("Authorization Server returned an error "
                                                                  + "(error: %s, description: %s)",
                                                          error,
                                                          description));
                }

                throw new AccessTokenException("AccessToken returned by the AuthorizationServer has a problem");
            }
        };
    }

    private boolean isResponseEmpty(final Response response) {
        // response.entity is NEVER null !!!
        return (response == null) || (response.getEntity() == null);
    }

    private boolean isOk(final Response response) {
        return Status.OK.equals(response.getStatus());
    }

    /**
     * Parse the response's content as a JSON structure.
     * @param entity stream response's content
     * @return {@link JsonValue} representing the JSON content
     * @throws AccessTokenException if there was some errors during parsing
     */
    private JsonValue asJson(final Entity entity) throws AccessTokenException {
        try {
            return new JsonValue(entity.getJson());
        } catch (IOException e) {
            // Do not use Entity.toString(), we probably don't want to fully output the content here
            throw new AccessTokenException("Cannot read response content as JSON", e);
        } finally {
            closeSilently(entity);
        }
    }


}
