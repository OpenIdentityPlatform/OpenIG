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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;
import static org.forgerock.http.URIUtil.withoutQueryAndFragment;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.util.Utils.closeSilently;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;

/**
 * Utility methods used by classes in this package.
 */
final class OAuth2Utils {

    static URI buildUri(final Exchange exchange, final Expression<String> uriExpression)
            throws ResponseException {
        return buildUri(exchange, uriExpression, null);
    }

    static URI buildUri(final Exchange exchange, final Expression<String> uriExpression,
            final String additionalPath) throws ResponseException {
        String uriString = null;
        try {
            uriString = uriExpression.eval(exchange);
            if (uriString == null) {
                throw new ResponseException(
                        format("The URI expression '%s' could not be resolved", uriExpression.toString()));
            }
            if (additionalPath != null) {
                if (uriString.endsWith("/")) {
                    uriString += additionalPath;
                } else {
                    uriString += "/" + additionalPath;
                }
            }
            // Resolve the computed Uri against the original Exchange URI
            return exchange.originalUri.resolve(new URI(uriString));
        } catch (final URISyntaxException e) {
            throw new ResponseException(format("Cannot build URI from %s", uriString), e);
        }
    }

    static JsonValue getJsonContent(final Response response) throws OAuth2ErrorException {
        try {
            return new JsonValue(response.getEntity().getJson()).expect(Map.class);
        } catch (final Exception e) {
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                    "Received a malformed JSON response from the authorization server", e);
        } finally {
            closeSilently(response);
        }
    }

    static List<String> getScopes(final Exchange exchange, final List<Expression<String>> scopeExpressions)
            throws ResponseException {
        final List<String> scopeValues = new ArrayList<String>(scopeExpressions.size());
        for (final Expression<String> scope : scopeExpressions) {
            final String result = scope.eval(exchange);
            if (result == null) {
                throw new ResponseException(format(
                        "The OAuth 2.0 client filter scope expression '%s' could not be resolved", scope.toString()));
            }
            scopeValues.add(result);
        }
        return scopeValues;
    }

    static Response httpRedirect(final String uri) {
        Response response = httpResponse(Status.FOUND);
        response.getHeaders().add(LocationHeader.NAME, uri);
        return response;
    }

    static Response httpResponse(final Status status) {
        Response response = new Response();
        response.setStatus(status);
        return response;
    }

    static boolean matchesUri(final Exchange exchange, final URI uri) {
        final URI pathOnly = withoutQueryAndFragment(uri);
        final URI requestPathOnly = withoutQueryAndFragment(exchange.originalUri);
        return pathOnly.equals(requestPathOnly);
    }

    private OAuth2Utils() {
        // Prevent instantiation.
    }

}
