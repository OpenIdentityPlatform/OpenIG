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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.client;

import static java.lang.String.format;
import static org.forgerock.http.util.Uris.create;
import static org.forgerock.http.util.Uris.withoutQueryAndFragment;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.authz.modules.oauth2.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Session.stateNew;
import static org.forgerock.util.Utils.closeSilently;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.services.context.Context;
import org.forgerock.util.Reject;
import org.forgerock.util.time.TimeService;

/**
 * Utility methods used by classes in this package.
 */
final class OAuth2Utils {

    static URI buildUri(final Context context, final Request request, final Expression<String> uriExpression)
            throws ResponseException {
        return buildUri(context, request, uriExpression, null);
    }

    static URI buildUri(final Context context, final Request request, final Expression<String> uriExpression,
                        final String additionalPath) throws ResponseException {

        String uriString = null;
        try {
            uriString = uriExpression.eval(bindings(context, request));
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
            // Resolve the computed Uri against the original Request URI
            UriRouterContext routerContext = context.asContext(UriRouterContext.class);
            return routerContext.getOriginalUri().resolve(new URI(uriString));
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

    static boolean matchesUri(final URI originalUri, final URI uri) {
        final URI pathOnly = withoutQueryAndFragment(uri);
        final URI requestPathOnly = withoutQueryAndFragment(originalUri);
        return pathOnly.equals(requestPathOnly);
    }

    static String createAuthorizationNonceHash(final String nonce) {
        /*
         * Do we want to use a cryptographic hash of the nonce? The primary goal
         * is to have something which is difficult to guess. However, if the
         * nonce is pushed to the user agent in a cookie, rather than stored
         * server side in a session, then it will be possible to construct a
         * cookie and state which have the same value and thereby create a fake
         * call-back from the authorization server. This will not be possible
         * using a CSRF, but a hacker might snoop the cookie and fake up a
         * call-back with a matching state. Is this threat possible? Even if it
         * is then I think the best approach is to secure the cookie, using a
         * JWT. And that's exactly what is planned.
         */
        return nonce;
    }

    static OAuth2Session loadOrCreateSession(final Context context,
                                             final Request request,
                                             final Expression<String> clientEndpoint,
                                             final TimeService time) throws OAuth2ErrorException,
                                                                            ResponseException {
        SessionContext sessionContext = context.asContext(SessionContext.class);
        final Object sessionJson = sessionContext.getSession().get(sessionKey(context,
                                                                        buildUri(context, request, clientEndpoint)));
        if (sessionJson != null) {
            return OAuth2Session.fromJson(time, new JsonValue(sessionJson));
        }
        return stateNew(time);
    }

    static void removeSession(final Context context,
                              final Request request,
                              final Expression<String> clientEndpoint) throws ResponseException {
        SessionContext sessionContext = context.asContext(SessionContext.class);
        sessionContext.getSession().remove(sessionKey(context, buildUri(context, request, clientEndpoint)));
    }

    static void saveSession(final Context context,
                            final OAuth2Session session,
                            final URI clientEndpoint) {
        SessionContext sessionContext = context.asContext(SessionContext.class);
        sessionContext.getSession().put(sessionKey(context, clientEndpoint), session.toJson().getObject());
    }

    static String sessionKey(final Context context, final URI clientEndpoint) {
        return "oauth2:" + clientEndpoint;
    }

    /**
     * Returns a new URI with the given query parameters appended to the original
     * ones, if any. The scheme, authority, path, and fragment remain unchanged.
     *
     * @param uri
     *            The URI whose query is to be changed, not {@code null}.
     * @param query
     *            The form containing the query parameters to add.
     * @return A new URI having the provided query parameters added to the given
     *         URI. The scheme, authority, path, and fragment remain unchanged.
     */
    public static URI appendQuery(final URI uri, final Form query) {
        Reject.ifNull(uri);

        if (query == null || query.isEmpty()) {
            return uri;
        }
        if (uri.getRawQuery() != null) {
            query.fromQueryString(uri.getRawQuery());
        }
        try {
            return create(uri.getScheme(),
                          uri.getRawUserInfo(),
                          uri.getHost(),
                          uri.getPort(),
                          uri.getRawPath(),
                          query.toQueryString(),
                          uri.getRawFragment());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private OAuth2Utils() {
        // Prevent instantiation.
    }

}
