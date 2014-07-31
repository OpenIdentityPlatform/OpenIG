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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.openig.util.URIUtil.withoutQueryAndFragment;
import static org.forgerock.util.Utils.closeSilently;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.RedirectFilter;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.LocationHeader;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;
import org.json.simple.parser.JSONParser;

/**
 * Utility methods used by classes in this package.
 */
final class OAuth2Utils {

    static URI buildUri(final Exchange exchange, final Expression uriExpression)
            throws HandlerException {
        return buildUri(exchange, uriExpression, null);
    }

    static URI buildUri(final Exchange exchange, final Expression uriExpression,
            final String additionalPath) throws HandlerException {
        try {
            String uriString = uriExpression.eval(exchange, String.class);
            if (uriString == null) {
                throw new HandlerException("Unable to evaluate URI expression");
            }
            if (additionalPath != null) {
                if (uriString.endsWith("/")) {
                    uriString += additionalPath;
                } else {
                    uriString += "/" + additionalPath;
                }
            }
            return exchange.request.getUri().resolve(new URI(uriString));
        } catch (final URISyntaxException e) {
            throw new HandlerException(e);
        }
    }

    static JsonValue getJsonContent(final Response response) throws OAuth2ErrorException {
        final JSONParser parser = new JSONParser();
        final InputStreamReader reader = new InputStreamReader(response.getEntity());
        try {
            return new JsonValue(parser.parse(reader)).expect(Map.class);
        } catch (final Exception e) {
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                    "Received a malformed JSON response from the authorization server", e);
        }
    }

    static void httpRedirect(final Exchange exchange, final String uri) {
        // FIXME: this constant should in HTTP package?
        httpResponse(exchange, RedirectFilter.REDIRECT_STATUS_302);
        exchange.response.getHeaders().add(LocationHeader.NAME, uri);
    }

    static void httpResponse(final Exchange exchange, final int status) {
        if (exchange.response != null) {
            closeSilently(exchange.response.getEntity());
        }
        exchange.response = new Response();
        exchange.response.setStatus(status);
    }

    static boolean matchesUri(final Exchange exchange, final URI uri) {
        final URI pathOnly = withoutQueryAndFragment(uri);
        final URI requestPathOnly = withoutQueryAndFragment(exchange.request.getUri());
        return pathOnly.equals(requestPathOnly);
    }

    private OAuth2Utils() {
        // Prevent instantiation.
    }

}
