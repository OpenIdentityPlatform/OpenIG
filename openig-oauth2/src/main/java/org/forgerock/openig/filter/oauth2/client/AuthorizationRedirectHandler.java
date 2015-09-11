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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static org.forgerock.http.util.Uris.urlDecode;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.openig.filter.oauth2.client.ClientRegistration.CLIENT_REG_KEY;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Session.stateNew;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpRedirect;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.util.Utils.joinAsString;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.http.protocol.Response.newResponsePromise;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.List;

import org.forgerock.services.context.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.TimeService;

/**
 * This terminal Handler has the responsibility to produce a 302 response for
 * the /authorize endpoint of the selected Issuer, configured with the
 * ClientRegistration info:
 *
 * <pre>
 * {@code
 * HTTP/1.1 302 Found
 * Location: https://server.example.com/authorize?           // from Issuer
 *     response_type=code
 *     &scope=openid%20profile%20email                       // from ClientRegistration
 *     &client_id=s6BhdRkqt3                                 // from ClientRegistration
 *     &state=af0ifjsldkj
 *     &redirect_uri=https%3A%2F%2Fclient.example.org%2Fcb   // from Client Registration or if none,
 *                                                              default to loginEndpoint callback URI.
 *     &login_hint=<input if available>                      // login_hint from OPENID RFC.
 * }
 * </pre>
 *
 * @see <a
 *      href="http://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">
 *      OpenID Connect Core 1.0 </a>
 */
class AuthorizationRedirectHandler implements Handler {
    @Override
    public Promise<Response, NeverThrowsException> handle(Context context, Request request) {
        final Exchange exchange = context.asContext(Exchange.class);
        final URI clientEndpoint = (URI) exchange.getAttributes().get("clientEndpoint");
        final String gotoUri = request.getForm().getFirst("goto");
        final ClientRegistration cr = (ClientRegistration) exchange.getAttributes().get(CLIENT_REG_KEY);
        final String loginHint = request.getForm().getFirst("discovery");

        if (cr != null && cr.getIssuer() != null) {
            final Issuer issuer = cr.getIssuer();
            final List<String> requestedScopes = cr.getScopes();
            final Form query = new Form();
            query.add("response_type", "code");
            query.add("client_id", cr.getClientId());
            if (cr.getRedirectUris() != null) {
                query.addAll("redirect_uri", cr.getRedirectUris());
            } else {
                query.putSingle("redirect_uri",  buildDefaultCallbackUri(clientEndpoint));
            }
            query.add("scope", joinAsString(" ", requestedScopes));
            if (loginHint != null && !loginHint.isEmpty()) {
                query.add("login_hint", loginHint);
            }

            /*
             * Construct the state parameter whose purpose is to prevent CSRF
             * attacks. The state will be passed back from the authorization
             * server once authorization has completed and the call-back will
             * verify that it received the same state that it sent originally by
             * comparing it with the value stored in the session or cookie
             * (depending on the persistence strategy).
             */
            final String nonce = createAuthorizationNonce();
            final String hash = createAuthorizationNonceHash(nonce);
            query.add("state", createAuthorizationState(hash, gotoUri));

            final String redirect = urlDecode(withQuery(issuer.getAuthorizeEndpoint(), query).toString());
            return newResponsePromise(httpRedirect(redirect)).then(
                    new Function<Response, Response, NeverThrowsException>() {
                        @Override
                        public Response apply(final Response response) {
                            /*
                             * Finally create and save the session. This may involve updating response cookies, so it is
                             * important to do it after creating the response.
                             */
                            try {
                                final String clientUri = clientEndpoint.toString();
                                final OAuth2Session session = stateNew(TimeService.SYSTEM)
                                                                .stateAuthorizing(cr.getName(),
                                                                                  clientUri,
                                                                                  nonce,
                                                                                  requestedScopes);
                                saveSession(exchange, session, clientEndpoint);
                                return response;
                            } catch (ResponseException e) {
                                return e.getResponse();
                            }
                        }
                    });
        } else {
            return newResultPromise(newInternalServerError("The selected client or its issuer is null. "
                                                           + "Authorization redirect aborted."));
        }
    }

    private String buildDefaultCallbackUri(final URI clientEndpoint) {
        try {
            return new URI(clientEndpoint + "/callback").toString();
        } catch (URISyntaxException ex) {
            // Should never happen
            return null;
        }
    }

    private String createAuthorizationNonce() {
        return new BigInteger(160, new SecureRandom()).toString(Character.MAX_RADIX);
    }

    private String createAuthorizationNonceHash(final String nonce) {
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

    private String createAuthorizationState(final String hash, final String gotoUri) {
        return gotoUri == null || gotoUri.isEmpty() ? hash : hash + ":" + gotoUri;
    }

    private void saveSession(Exchange exchange,
                             OAuth2Session session,
                             final URI clientEndpoint) throws ResponseException {
        exchange.getSession().put(sessionKey(exchange, clientEndpoint), session.toJson().getObject());
    }

    private String sessionKey(final Exchange exchange, final URI clientEndpoint) {
        return "oauth2:" + clientEndpoint;
    }
}
