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

import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.*;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Session.*;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.*;
import static org.forgerock.openig.heap.HeapUtil.*;
import static org.forgerock.openig.util.JsonValueUtil.*;
import static org.forgerock.openig.util.URIUtil.*;
import static org.forgerock.util.Utils.*;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.GenericFilter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Form;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.util.time.TimeService;

/**
 * A filter which is responsible for authenticating the end-user using OAuth 2.0
 * delegated authorization. The filter does the following depending on the
 * incoming request URI:
 * <ul>
 * <li><code>{clientEndpoint}/login/{provider}?goto=&lt;url></code> - redirects
 * the user for authorization against the specified provider
 * <li><code>{clientEndpoint}/logout?goto=&lt;url></code> - removes
 * authorization state for the end-user
 * <li><code>{clientEndpoint}/callback</code> - OAuth 2.0 authorization
 * call-back end-point (state encodes nonce, goto, and provider)
 * <li>all other requests - restores authorization state and places it in the
 * target location.
 * </ul>
 * <p>
 * Configuration options:
 *
 * <pre>
 * "target"                       : expression,         [REQUIRED]
 * "scopes"                       : [ expressions ],    [REQUIRED]
 * "clientEndpoint"               : expression,         [REQUIRED]
 * "loginHandler"                 : handler,            [REQUIRED - if more than one provider]
 * "failureHandler"               : handler,            [REQUIRED]
 * "providerHandler"              : handler,            [REQUIRED]
 * "defaultLoginGoto"             : expression,         [OPTIONAL - default return empty page]
 * "defaultLogoutGoto"            : expression,         [OPTIONAL - default return empty page]
 * "requireLogin"                 : boolean             [OPTIONAL - default require login]
 * "requireHttps"                 : boolean             [OPTIONAL - default require SSL]
 * "useJWTSession"                : boolean,            [OPTIONAL - default use Servlet session]
 * "providers"                    : array [
 *     "wellKnownConfiguration"       : String,         [OPTIONAL - if authorize and token end-points are specified]
 *     "authorizeEndpoint"            : uriExpression,  [REQUIRED - if no well-known configuration]
 *     "tokenEndpoint"                : uriExpression,  [REQUIRED - if no well-known configuration]
 *     "userInfoEndpoint"             : uriExpression,  [OPTIONAL - default no user info]
 *     "clientId"                     : expression,     [REQUIRED]
 *     "clientSecret"                 : expression,     [REQUIRED]
 * ]
 * </pre>
 *
 * For example:
 *
 * <pre>
 * {
 *     "name": "OpenIDConnect",
 *     "type": "org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter",
 *     "config": {,
 *         "target"                : "${exchange.openid}",
 *         "scopes"                : ["openid","profile","email"],
 *         "clientEndpoint"        : "/openid",
 *         "loginHandler"          : "NascarPage",
 *         "failureHandler"        : "LoginFailed",
 *         "providerHandler"       : "ClientHandler",
 *         "defaultLoginGoto"      : "/homepage",
 *         "defaultLogoutGoto"     : "/loggedOut",
 *         "requireHttps"          : false,
 *         "requireLogin"          : true,
 *         "providers"  : [
 *             {
 *                 "name"          : "openam",
 *                 "wellKnownConfiguration"
 *                                 : "https://openam.example.com:8080/openam/.well-known/openid-configuration",
 *                 "clientId"      : "*****",
 *                 "clientSecret"  : "*****"
 *             },
 *             {
 *                 "name"          : "google",
 *                 "wellKnownConfiguration"
 *                                 : "https://accounts.google.com/.well-known/openid-configuration",
 *                 "clientId"      : "*****",
 *                 "clientSecret"  : "*****"
 *             }
 *         ]
 *     }
 * }
 * </pre>
 *
 * Once authorization, this filter will inject the following information into
 * the target location:
 *
 * <pre>
 * "openid" : {
 *         "provider"           : "google",
 *         "access_token"       : "xxx",
 *         "id_token"           : "xxx",
 *         "token_type"         : "Bearer",
 *         "expires_in"         : 3599,
 *         "scope"              : [ "openid", "profile", "email" ],
 *         "client_endpoint"    : "http://www.example.com:8081/openid",
 *         "id_token_claims"    : {
 *             "at_hash"            : "xxx",
 *             "sub"                : "xxx",
 *             "aud"                : [ "xxx.apps.googleusercontent.com" ],
 *             "email_verified"     : true,
 *             "azp"                : "xxx.apps.googleusercontent.com",
 *             "iss"                : "accounts.google.com",
 *             "exp"                : "2014-07-25T00:12:53+0000",
 *             "iat"                : "2014-07-24T23:07:53+0000",
 *             "email"              : "micky.mouse@gmail.com"
 *         },
 *         "user_info"          : {
 *             "sub"                : "xxx",
 *             "email_verified"     : "true",
 *             "gender"             : "male",
 *             "kind"               : "plus#personOpenIdConnect",
 *             "profile"            : "https://plus.google.com/xxx",
 *             "name"               : "Micky Mouse",
 *             "given_name"         : "Micky",
 *             "locale"             : "en-GB",
 *             "family_name"        : "Mouse",
 *             "picture"            : "https://lh4.googleusercontent.com/xxx/photo.jpg?sz=50",
 *             "email"              : "micky.mouse@gmail.com"
 *         }
 *     }
 * }
 * </pre>
 */
public final class OAuth2ClientFilter extends GenericFilter {
    private Expression clientEndpoint;
    private Expression defaultLoginGoto;
    private Expression defaultLogoutGoto;
    private Handler failureHandler;
    private Handler loginHandler;
    private OAuth2SessionPersistenceStrategy persistenceStrategy;
    private Handler providerHandler;
    private final Map<String, OAuth2Provider> providers =
            new LinkedHashMap<String, OAuth2Provider>();
    private boolean requireHttps = true;
    private boolean requireLogin = true;
    private List<Expression> scopes;
    private Expression target;
    private TimeService time = TimeService.SYSTEM;

    /**
     * Adds an authorization provider. At least one provider must be specified,
     * and if there are more than one then a login handler must also be
     * specified.
     *
     * @param provider
     *            The authorization provider.
     * @return This filter.
     */
    public OAuth2ClientFilter addProvider(final OAuth2Provider provider) {
        this.providers.put(provider.getName(), provider);
        return this;
    }

    @Override
    public void filter(final Exchange exchange, final Handler next) throws HandlerException,
            IOException {
        final LogTimer timer = logger.getTimer().start();
        try {
            // Login: {clientEndpoint}/login?provider={name}[&goto={url}]
            if (matchesUri(exchange, buildLoginUri(exchange))) {
                checkRequestIsSufficientlySecure(exchange);
                handleUserInitiatedLogin(exchange);
                return;
            }

            // Authorize call-back: {clientEndpoint}/callback?...
            if (matchesUri(exchange, buildCallbackUri(exchange))) {
                checkRequestIsSufficientlySecure(exchange);
                handleAuthorizationCallback(exchange);
                return;
            }

            // Logout: {clientEndpoint}/logout[?goto={url}]
            if (matchesUri(exchange, buildLogoutUri(exchange))) {
                handleUserInitiatedLogout(exchange);
                return;
            }

            // Everything else...
            handleProtectedResource(exchange, next);
        } catch (final OAuth2ErrorException e) {
            handleOAuth2ErrorException(exchange, e);
        } finally {
            timer.stop();
        }
    }

    /**
     * Sets the expression which will be used for obtaining the base URI for the
     * following client end-points:
     * <ul>
     * <li><tt>{endpoint}/callback</tt> - called by the authorization server
     * once authorization has completed
     * <li><tt>{endpoint}/login?provider={name}[&goto={url}]</tt> - user
     * end-point for performing user initiated authentication, such as from a
     * "login" link or "NASCAR" login page. Supports a "goto" URL parameter
     * which will be invoked once the login completes, e.g. to take the user to
     * their personal home page
     * <li><tt>{endpoint}/logout[?goto={url}]</tt> - user end-point for
     * performing user initiated logout, such as from a "logout" link. Supports
     * a "goto" URL parameter which will be invoked once the logout completes,
     * e.g. to take the user to generic home page.
     * </ul>
     * This configuration parameter is required.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the base URI
     *            for the client end-points.
     * @return This filter.
     */
    public OAuth2ClientFilter setClientEndpoint(final Expression endpoint) {
        this.clientEndpoint = endpoint;
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the default login
     * "goto" URI. The default goto URI will be used when a user performs a user
     * initiated login without providing a "goto" http parameter. This
     * configuration parameter is optional. If no "goto" parameter is provided
     * in the request and there is no default "goto" then user initiated login
     * requests will simply return a 200 status.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the default
     *            login "goto" URI.
     * @return This filter.
     */
    public OAuth2ClientFilter setDefaultLoginGoto(final Expression endpoint) {
        this.defaultLoginGoto = endpoint;
        return this;
    }

    /**
     * Sets the expression which will be used for obtaining the default logout
     * "goto" URI. The default goto URI will be used when a user performs a user
     * initiated logout without providing a "goto" http parameter. This
     * configuration parameter is optional. If no "goto" parameter is provided
     * in the request and there is no default "goto" then user initiated logout
     * requests will simply return a 200 status.
     *
     * @param endpoint
     *            The expression which will be used for obtaining the default
     *            logout "goto" URI.
     * @return This filter.
     */
    public OAuth2ClientFilter setDefaultLogoutGoto(final Expression endpoint) {
        this.defaultLogoutGoto = endpoint;
        return this;
    }

    /**
     * Sets the handler which will be invoked when authentication fails. This
     * configuration parameter is required. If authorization fails for any
     * reason and the request cannot be processed using the next filter/handler,
     * then the request will be forwarded to the failure handler. In addition,
     * the {@code exchange} target will be populated with the following OAuth
     * 2.0 error information:
     *
     * <pre>
     * &lt;target> : {
     *     "provider"           : "google",
     *     "error"              : {
     *         "realm"              : string,          [OPTIONAL]
     *         "scope"              : array of string, [OPTIONAL list of required scopes]
     *         "error"              : string,          [OPTIONAL]
     *         "error_description"  : string,          [OPTIONAL]
     *         "error_uri"          : string           [OPTIONAL]
     *     },
     *     // The following fields may or may not be present depending on
     *     // how far authorization proceeded.
     *     "access_token"       : "xxx",
     *     "id_token"           : "xxx",
     *     "token_type"         : "Bearer",
     *     "expires_in"         : 3599,
     *     "scope"              : [ "openid", "profile", "email" ],
     *     "client_endpoint"    : "http://www.example.com:8081/openid",
     * }
     * </pre>
     *
     * See {@link OAuth2Error} for a detailed description of the various error
     * fields and their possible values.
     *
     * @param handler
     *            The handler which will be invoked when authentication fails.
     * @return This filter.
     */
    public OAuth2ClientFilter setFailureHandler(final Handler handler) {
        this.failureHandler = handler;
        return this;
    }

    /**
     * Sets the handler which will be invoked when the user needs to
     * authenticate. This configuration parameter is required if there are more
     * than one providers configured.
     *
     * @param handler
     *            The handler which will be invoked when the user needs to
     *            authenticate.
     * @return This filter.
     */
    public OAuth2ClientFilter setLoginHandler(final Handler handler) {
        this.loginHandler = handler;
        return this;
    }

    /**
     * Specifies how OAuth2 session state information will be persisted between
     * successive HTTP requests. This configuration parameter is required.
     *
     * @param strategy
     *            The strategy to use for persisting OAuth2 session state
     *            information between successive HTTP requests.
     * @return This filter.
     */
    public OAuth2ClientFilter setPersistenceStrategy(final OAuth2SessionPersistenceStrategy strategy) {
        this.persistenceStrategy = strategy;
        return this;
    }

    /**
     * Sets the handler which will be used for communicating with the
     * authorization server. This configuration parameter is required.
     *
     * @param handler
     *            The handler which will be used for communicating with the
     *            authorization server.
     * @return This filter.
     */
    public OAuth2ClientFilter setProviderHandler(final Handler handler) {
        this.providerHandler = handler;
        return this;
    }

    /**
     * Specifies whether all incoming requests must use TLS. This configuration
     * parameter is optional and set to {@code true} by default.
     *
     * @param requireHttps
     *            {@code true} if all incoming requests must use TLS,
     *            {@code false} by default.
     * @return This filter.
     */
    public OAuth2ClientFilter setRequireHttps(final boolean requireHttps) {
        this.requireHttps = requireHttps;
        return this;
    }

    /**
     * Specifies whether authentication is required for all incoming requests.
     * This configuration parameter is optional and set to {@code true} by
     * default.
     *
     * @param requireLogin
     *            {@code true} if authentication is required for all incoming
     *            requests, or {@code false} if authentication should be
     *            performed only when required (default {@code true}.
     * @return This filter.
     */
    public OAuth2ClientFilter setRequireLogin(final boolean requireLogin) {
        this.requireLogin = requireLogin;
        return this;
    }

    /**
     * Sets the expressions which will be used for obtaining the OAuth 2 scopes.
     * This configuration parameter is required.
     *
     * @param scopes
     *            The expressions which will be used for obtaining the OAuth 2
     *            scopes.
     * @return This filter.
     */
    public OAuth2ClientFilter setScopes(final List<Expression> scopes) {
        this.scopes = scopes;
        return this;
    }

    /**
     * Sets the expression which will be used for storing authorization
     * information in the exchange. This configuration parameter is required.
     *
     * @param target
     *            The expression which will be used for storing authorization
     *            information in the exchange.
     * @return This filter.
     */
    public OAuth2ClientFilter setTarget(final Expression target) {
        this.target = target;
        return this;
    }

    /**
     * Sets the time service which will be used for determining a token's
     * expiration time. By default {@link TimeService#SYSTEM} will be used. This
     * method is intended for unit testing.
     *
     * @param time
     *            The time service which will be used for determining a token's
     *            expiration time.
     * @return This filter.
     */
    OAuth2ClientFilter setTime(final TimeService time) {
        this.time = time;
        return this;
    }

    private URI buildCallbackUri(final Exchange exchange) throws HandlerException {
        return buildUri(exchange, clientEndpoint, "callback");
    }

    private URI buildLoginUri(final Exchange exchange) throws HandlerException {
        return buildUri(exchange, clientEndpoint, "login");
    }

    private URI buildLogoutUri(final Exchange exchange) throws HandlerException {
        return buildUri(exchange, clientEndpoint, "logout");
    }

    private void checkRequestIsSufficientlySecure(final Exchange exchange)
            throws OAuth2ErrorException {
        // FIXME: use enforce filter?
        if (requireHttps && !exchange.request.getUri().getScheme().equalsIgnoreCase("https")) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "SSL is required in order to perform this operation");
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
         * cookie and state which have the same valueand thereby create a fake
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

    private OAuth2Provider getProvider(final OAuth2Session session) {
        final String providerName = session.getProviderName();
        return providerName != null ? providers.get(providerName) : null;
    }

    private List<String> getScopes(final Exchange exchange) throws HandlerException {
        final List<String> scopeValues = new ArrayList<String>(scopes.size());
        for (final Expression scope : scopes) {
            final String result = scope.eval(exchange, String.class);
            if (result == null) {
                throw new HandlerException("Unable to determine the scope");
            }
            scopeValues.add(result);
        }
        return scopeValues;
    }

    private void handleAuthorizationCallback(final Exchange exchange) throws HandlerException,
            OAuth2ErrorException {
        if (!"GET".equals(exchange.request.getMethod())) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization call-back failed because the request was not a GET");
        }

        /*
         * The state must be valid regardless of whether the authorization
         * succeeded or failed.
         */
        final String state = exchange.request.getForm().getFirst("state");
        if (state == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization call-back failed because there was no state parameter");
        }
        final OAuth2Session session = loadOrCreateSession(exchange);
        if (!session.isAuthorizing()) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization call-back failed because there is no authorization in progress");
        }
        final int colonPos = state.indexOf(':');
        final String actualHash = colonPos < 0 ? state : state.substring(0, colonPos);
        final String gotoUri = colonPos < 0 ? null : state.substring(colonPos + 1);
        final String expectedHash =
                createAuthorizationNonceHash(session.getAuthorizationRequestNonce());
        if (!expectedHash.equals(actualHash)) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization call-back failed because the state parameter contained "
                            + "an unexpected value");
        }

        final OAuth2Provider provider = getProvider(session);
        if (provider == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization call-back failed because the provider name was unrecognized");
        }

        final String code = exchange.request.getForm().getFirst("code");
        if (code == null) {
            throw new OAuth2ErrorException(OAuth2Error.valueOfForm(exchange.request.getForm()));
        }

        /*
         * Exchange the authorization code for an access token and optional ID
         * token, and then update the session state.
         */
        final Request request =
                provider.createRequestForAccessToken(exchange, code, buildCallbackUri(exchange)
                        .toString());
        final Response response = httpRequestToAuthorizationServer(exchange, request);
        if (response.getStatus() != 200) {
            if (response.getStatus() == 400 || response.getStatus() == 401) {
                final JsonValue errorJson = getJsonContent(response);
                throw new OAuth2ErrorException(OAuth2Error.valueOfJsonContent(errorJson.asMap()));
            } else {
                throw new OAuth2ErrorException(E_SERVER_ERROR, String.format(
                        "Unable to exchange access token [status=%d]", response.getStatus()));
            }
        }

        // FIXME: perform additional ID token validation using CAF.
        final JsonValue accessTokenResponse = getJsonContent(response);
        final SignedJwt decodedJwtToken = provider.extractIdToken(accessTokenResponse);

        /*
         * Finally complete the authorization request by redirecting to the
         * original goto URI and saving the session. It is important to save the
         * session after setting the response because it may need to access
         * response cookies.
         */
        final OAuth2Session authorizedSession =
                session.stateAuthorized(accessTokenResponse, decodedJwtToken);
        httpRedirectGoto(exchange, gotoUri, defaultLoginGoto);
        persistenceStrategy.save(sessionKey(exchange), exchange, authorizedSession);
    }

    private void handleOAuth2ErrorException(final Exchange exchange, final OAuth2ErrorException e)
            throws HandlerException, IOException {
        final OAuth2Error error = e.getOAuth2Error();
        if (error.is(E_ACCESS_DENIED) || error.is(E_INVALID_TOKEN)) {
            logger.debug(e.getMessage());
        } else {
            // Assume all other errors are more serious operational errors.
            logger.warning(e.getMessage());
        }
        final OAuth2Session session = loadOrCreateSession(exchange);
        final Map<String, Object> info =
                new LinkedHashMap<String, Object>(session.getAccessTokenResponse());
        // Override these with effective values.
        info.put("provider", session.getProviderName());
        info.put("client_endpoint", session.getClientEndpoint());
        info.put("expires_in", session.getExpiresIn());
        info.put("scope", session.getScopes());
        final SignedJwt idToken = session.getIdToken();
        if (idToken != null) {
            final Map<String, Object> idTokenClaims = new LinkedHashMap<String, Object>();
            for (final String claim : idToken.getClaimsSet().keys()) {
                idTokenClaims.put(claim, idToken.getClaimsSet().getClaim(claim));
            }
            info.put("id_token_claims", idTokenClaims);
        }
        info.put("error", error.toJsonContent());
        target.set(exchange, info);
        failureHandler.handle(exchange);
    }

    private void handleProtectedResource(final Exchange exchange, final Handler next)
            throws HandlerException, IOException, OAuth2ErrorException {
        final OAuth2Session session = loadOrCreateSession(exchange);
        if (!session.isAuthorized() && requireLogin) {
            sendRedirectForAuthorization(exchange);
            return;
        }
        final OAuth2Session refreshedSession =
                session.isAuthorized() ? prepareExchange(exchange, session) : session;
        next.handle(exchange);
        if (exchange.response.getStatus() == 401 && !session.isAuthorized()) {
            closeSilently(exchange.response);
            exchange.response = null;
            sendRedirectForAuthorization(exchange);
        } else {
            persistenceStrategy.save(sessionKey(exchange), exchange, refreshedSession);
        }
    }

    private void handleResourceAccessFailure(final Response response) throws OAuth2ErrorException {
        final OAuth2BearerWWWAuthenticateHeader header =
                new OAuth2BearerWWWAuthenticateHeader(response);
        final OAuth2Error error = header.getOAuth2Error();
        final OAuth2Error bestEffort =
                OAuth2Error.bestEffortResourceServerError(response.getStatus(), error);
        throw new OAuth2ErrorException(bestEffort);
    }

    private void handleUserInitiatedLogin(final Exchange exchange) throws HandlerException,
            OAuth2ErrorException {
        final String providerName = exchange.request.getForm().getFirst("provider");
        final String gotoUri = exchange.request.getForm().getFirst("goto");
        if (providerName == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization provider must be specified");
        }
        final OAuth2Provider provider = providers.get(providerName);
        if (provider == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST, "Authorization provider '"
                    + providerName + "' was not recognized");
        }
        sendAuthorizationRedirect(exchange, provider, gotoUri);
    }

    private void handleUserInitiatedLogout(final Exchange exchange) throws HandlerException {
        final String gotoUri = exchange.request.getForm().getFirst("goto");
        httpRedirectGoto(exchange, gotoUri, defaultLogoutGoto);
        persistenceStrategy.remove(sessionKey(exchange), exchange);
    }

    private void httpRedirectGoto(final Exchange exchange, final String gotoUri,
            final Expression defaultGotoUri) throws HandlerException {
        if (gotoUri != null) {
            httpRedirect(exchange, gotoUri);
        } else if (defaultGotoUri != null) {
            httpRedirect(exchange, buildUri(exchange, defaultGotoUri).toString());
        } else {
            httpResponse(exchange, 200);
        }
    }

    private Response httpRequestToAuthorizationServer(final Exchange exchange, final Request request)
            throws OAuth2ErrorException, HandlerException {
        final Request savedRequest = exchange.request;
        final Response savedResponse = exchange.response;
        exchange.request = request;
        try {
            providerHandler.handle(exchange);
            return exchange.response;
        } catch (final IOException e) {
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                    "Authorization failed because an error occurred while trying "
                            + "to contact the authorization server");
        } finally {
            exchange.request = savedRequest;
            exchange.response = savedResponse;
        }
    }

    private OAuth2Session loadOrCreateSession(final Exchange exchange) throws HandlerException {
        final OAuth2Session session = persistenceStrategy.load(sessionKey(exchange), exchange);
        return session != null ? session : stateNew(time);
    }

    private OAuth2Session prepareExchange(final Exchange exchange, final OAuth2Session session)
            throws HandlerException, OAuth2ErrorException {
        try {
            tryPrepareExchange(exchange, session);
            return session;
        } catch (final OAuth2ErrorException e) {
            /*
             * Try again if the access token looks like it has expired and can
             * be refreshed.
             */
            final OAuth2Error error = e.getOAuth2Error();
            final OAuth2Provider provider = getProvider(session);
            if (error.is(E_INVALID_TOKEN) && provider != null && session.getRefreshToken() != null) {
                final Request request = provider.createRequestForTokenRefresh(exchange, session);
                final Response response = httpRequestToAuthorizationServer(exchange, request);
                if (response.getStatus() == 200) {
                    // Update session with new access token.
                    final JsonValue accessTokenResponse = getJsonContent(response);
                    final SignedJwt decodedJwtToken = provider.extractIdToken(accessTokenResponse);
                    final OAuth2Session refreshedSession =
                            session.stateRefreshed(accessTokenResponse, decodedJwtToken);
                    tryPrepareExchange(exchange, refreshedSession);
                    return refreshedSession;
                }
                if (response.getStatus() == 400 || response.getStatus() == 401) {
                    final JsonValue errorJson = getJsonContent(response);
                    throw new OAuth2ErrorException(OAuth2Error
                            .valueOfJsonContent(errorJson.asMap()));
                } else {
                    throw new OAuth2ErrorException(E_SERVER_ERROR, String.format(
                            "Unable to refresh access token [status=%d]", response.getStatus()));
                }
            }

            /*
             * It looks like the token cannot be refreshed or something more
             * serious happened, e.g. the token has the wrong scopes. Re-throw
             * the error and let the failure-handler deal with it.
             */
            throw e;
        }
    }

    private void sendAuthorizationRedirect(final Exchange exchange, final OAuth2Provider provider,
            final String gotoUri) throws HandlerException {
        final URI uri = provider.getAuthorizeEndpoint(exchange);
        final List<String> requestedScopes = getScopes(exchange);
        final Form query = new Form();
        if (uri.getRawQuery() != null) {
            query.fromString(uri.getRawQuery());
        }
        query.add("response_type", "code");
        query.add("client_id", provider.getClientId(exchange));
        query.add("redirect_uri", buildCallbackUri(exchange).toString());
        query.add("scope", joinAsString(" ", requestedScopes));

        /*
         * Construct the state parameter whose purpose is to prevent CSRF
         * attacks. The state will be passed back from the authorization server
         * once authorization has completed and the call-back will verify that
         * it received the same state that it sent originally by comparing it
         * with the value stored in the session or cookie (depending on the
         * persistence strategy).
         */
        final String nonce = createAuthorizationNonce();
        final String hash = createAuthorizationNonceHash(nonce);
        query.add("state", createAuthorizationState(hash, gotoUri));

        final String redirect = withQuery(uri, query).toString();
        httpRedirect(exchange, redirect);

        /*
         * Finally create and save the session. This may involve updating
         * response cookies, so it is important to do it after creating the
         * response.
         */
        final String clientUri = buildUri(exchange, clientEndpoint).toString();
        final OAuth2Session session =
                stateNew(time).stateAuthorizing(provider.getName(), clientUri, nonce,
                        requestedScopes);
        persistenceStrategy.save(sessionKey(exchange), exchange, session);
    }

    private void sendRedirectForAuthorization(final Exchange exchange) throws HandlerException,
            IOException {
        if (loginHandler != null) {
            loginHandler.handle(exchange);
        } else {
            final OAuth2Provider provider = providers.values().iterator().next();
            sendAuthorizationRedirect(exchange, provider, exchange.request.getUri().toString());
        }
    }

    private String sessionKey(final Exchange exchange) throws HandlerException {
        return "oauth2:" + buildUri(exchange, clientEndpoint);
    }

    private void tryPrepareExchange(final Exchange exchange, final OAuth2Session session)
            throws HandlerException, OAuth2ErrorException {
        final Map<String, Object> info =
                new LinkedHashMap<String, Object>(session.getAccessTokenResponse());
        // Override these with effective values.
        info.put("provider", session.getProviderName());
        info.put("client_endpoint", session.getClientEndpoint());
        info.put("expires_in", session.getExpiresIn());
        info.put("scope", session.getScopes());
        final SignedJwt idToken = session.getIdToken();
        if (idToken != null) {
            final Map<String, Object> idTokenClaims = new LinkedHashMap<String, Object>();
            for (final String claim : idToken.getClaimsSet().keys()) {
                idTokenClaims.put(claim, idToken.getClaimsSet().getClaim(claim));
            }
            info.put("id_token_claims", idTokenClaims);
        }

        final OAuth2Provider provider = getProvider(session);
        if (provider != null && provider.hasUserInfoEndpoint()
                && session.getScopes().contains("openid")) {
            final Request request =
                    provider.createRequestForUserInfo(exchange, session.getAccessToken());
            final Response response = httpRequestToAuthorizationServer(exchange, request);
            if (response.getStatus() != 200) {
                /*
                 * The access token may have expired. Trigger an exception,
                 * catch it and react later.
                 */
                handleResourceAccessFailure(response);
            }
            final JsonValue userInfoResponse = getJsonContent(response);
            info.put("user_info", userInfoResponse.asMap());
        }
        target.set(exchange, info);
    }

    /** Creates and initializes the filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            final OAuth2ClientFilter filter = new OAuth2ClientFilter();
            filter.setTarget(asExpression(config.get("target")));
            filter.setScopes(config.get("scopes").required().asList(ofExpression()));
            filter.setClientEndpoint(asExpression(config.get("clientEndpoint").required()));
            final Handler loginHandler = getObject(heap, config.get("loginHandler"), Handler.class);
            filter.setLoginHandler(loginHandler);
            filter.setFailureHandler(getRequiredObject(heap, config.get("failureHandler"),
                    Handler.class));
            final Handler providerHandler =
                    getRequiredObject(heap, config.get("providerHandler"), Handler.class);
            filter.setProviderHandler(providerHandler);
            filter.setDefaultLoginGoto(asExpression(config.get("defaultLoginGoto")));
            filter.setDefaultLogoutGoto(asExpression(config.get("defaultLogoutGoto")));
            filter.setRequireHttps(config.get("requireHttps").defaultTo(true).asBoolean());
            filter.setRequireLogin(config.get("requireLogin").defaultTo(true).asBoolean());
            final boolean useJWTSession = config.get("useJWTSession").defaultTo(false).asBoolean();
            if (useJWTSession) {
                throw new HeapException("OAuth2 JWT session persistence is not supported yet");
            }
            filter.setPersistenceStrategy(OAuth2SessionPersistenceStrategy.SESSION);
            int providerCount = 0;
            for (final JsonValue providerConfig : config.get("providers").required()) {
                // Must set the authorization handler before using well-known config.
                final OAuth2Provider provider =
                        new OAuth2Provider(providerConfig.get("name").required().asString());
                provider.setClientId(asExpression(providerConfig.get("clientId").required()));
                provider.setClientSecret(asExpression(providerConfig.get("clientSecret").required()));

                JsonValue knownConfiguration = providerConfig.get("wellKnownConfiguration");
                if (!knownConfiguration.isNull()) {
                    final URI uri = knownConfiguration.asURI();
                    if (uri != null) {
                        final Exchange exchange = new Exchange();
                        exchange.request = new Request();
                        exchange.request.setMethod("GET");
                        exchange.request.setUri(uri);
                        try {
                            providerHandler.handle(exchange);
                            if (exchange.response.getStatus() != 200) {
                                throw new HeapException(
                                        "Unable to read well-known OpenID Configuration from '"
                                                + exchange.request.getUri().toString() + "'");
                            }
                            provider.setWellKnownConfiguration(getJsonContent(exchange.response));
                        } catch (final Exception e) {
                            throw new HeapException(
                                    "Unable to read well-known OpenID Configuration from '"
                                            + exchange.request.getUri().toString() + "'", e);
                        } finally {
                            closeSilently(exchange.response);
                        }
                    }
                } else {
                    provider.setAuthorizeEndpoint(asExpression(providerConfig.get(
                            "authorizeEndpoint").required()));
                    provider.setTokenEndpoint(asExpression(providerConfig.get("tokenEndpoint")
                            .required()));
                    provider.setUserInfoEndpoint(asExpression(providerConfig
                            .get("userInfoEndpoint")));
                }
                filter.addProvider(provider);
                providerCount++;
            }
            if (providerCount == 0) {
                throw new HeapException("At least one authorization provider must be specified");
            }
            if (loginHandler == null && providerCount > 1) {
                throw new HeapException(
                        "A login handler must be specified when there are multiple providers");
            }
            return filter;
        }

    }
}
