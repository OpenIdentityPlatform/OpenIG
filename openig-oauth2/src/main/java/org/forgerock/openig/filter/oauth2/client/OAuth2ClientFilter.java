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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_ACCESS_DENIED;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_INVALID_REQUEST;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_INVALID_TOKEN;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Session.stateNew;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.buildUri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpRedirect;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpResponse;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.matchesUri;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.JsonValues.ofExpression;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.Utils.joinAsString;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.oauth2.cache.ThreadSafeCache;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Factory;
import org.forgerock.util.Function;
import org.forgerock.util.LazyMap;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A filter which is responsible for authenticating the end-user using OAuth 2.0
 * delegated authorization. The filter does the following depending on the
 * incoming request URI:
 * <ul>
 * <li>{@code {clientEndpoint}/login/{provider}?goto=<url>} - redirects
 * the user for authorization against the specified provider
 * <li>{@code {clientEndpoint}/login?{*}discovery={input}&goto=<url>} -
 * performs issuer discovery and dynamic client registration if possible on
 * the given user input and redirects the user to the client endpoint.
 * <li>{@code {clientEndpoint}/logout?goto=<url>} - removes
 * authorization state for the end-user
 * <li>{@code {clientEndpoint}/callback} - OAuth 2.0 authorization
 * call-back end-point (state encodes nonce, goto, and provider)
 * <li>all other requests - restores authorization state and places it in the
 * target location.
 * </ul>
 * <p>
 * Configuration options:
 *
 * <pre>
 * {@code
 * "target"                       : expression,         [OPTIONAL - default is ${exchange.openid}]
 * "scopes"                       : [ expressions ],    [OPTIONAL]
 * "clientEndpoint"               : expression,         [REQUIRED]
 * "loginEndpoint"                : handler,            [OPTIONAL - for issuer discovery
 *                                                                  and dynamic client registration]
 * "loginHandler"                 : handler,            [REQUIRED - if more than one provider]
 * "failureHandler"               : handler,            [REQUIRED]
 * "defaultLoginGoto"             : expression,         [OPTIONAL - default return empty page]
 * "defaultLogoutGoto"            : expression,         [OPTIONAL - default return empty page]
 * "requireLogin"                 : boolean             [OPTIONAL - default require login]
 * "requireHttps"                 : boolean             [OPTIONAL - default require SSL]
 * "cacheExpiration"              : duration            [OPTIONAL - default to 20 seconds]
 * "providers"                    : [ strings ]         [REQUIRED]
 * }
 * </pre>
 *
 * For example:
 *
 * <pre>
 * {@code
 * {
 *     "name": "OpenIDConnect",
 *     "type": "org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter",
 *     "config": {
 *         "target"                : "${exchange.openid}",
 *         "scopes"                : ["openid","profile","email"],
 *         "clientEndpoint"        : "/openid",
 *         "loginEndpoint"         : "myLoginEndpointHandler"
 *         "loginHandler"          : "NascarPage",
 *         "failureHandler"        : "LoginFailed",
 *         "defaultLoginGoto"      : "/homepage",
 *         "defaultLogoutGoto"     : "/loggedOut",
 *         "requireHttps"          : false,
 *         "requireLogin"          : true,
 *         "providers"             : [ "openam", "google" ]
 *     }
 * }
 * }
 * </pre>
 *
 * Once authorized, this filter will inject the following information into
 * the target location:
 *
 * <pre>
 * {@code
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
 * }
 * </pre>
 */
public final class OAuth2ClientFilter extends GenericHeapObject implements Filter {

    /** The expression which will be used for storing authorization information in the exchange. */
    public static final String DEFAULT_TOKEN_KEY = "openid";

    private Expression<String> clientEndpoint;
    private Expression<String> defaultLoginGoto;
    private Expression<String> defaultLogoutGoto;
    private Handler failureHandler;
    private Handler loginHandler;
    private Handler loginEndpoint;
    private final Map<String, OAuth2Provider> providers =
            new LinkedHashMap<>();
    private boolean requireHttps = true;
    private boolean requireLogin = true;
    private List<Expression<String>> scopes;
    private Expression<?> target;
    private final TimeService time;
    private ThreadSafeCache<String, Map<String, Object>> userInfoCache;
    private final Heap heap;

    /**
     * Constructs an {@link OAuth2ClientFilter}.
     *
     * @param time
     *            The TimeService to use.
     * @param heap
     *            The current heap.
     */
    public OAuth2ClientFilter(TimeService time, Heap heap) {
        this.time = time;
        this.heap = heap;
    }

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
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        Exchange exchange = context.asContext(Exchange.class);
        try {
            // Login: {clientEndpoint}/login
            if (matchesUri(exchange, buildLoginUri(exchange))) {
                if (request.getForm().containsKey("discovery")) {
                    // User input: {clientEndpoint}/login?discovery={input}[&goto={url}]
                    return handleUserInitiatedDiscovery(request, context);
                } else {
                    // Login: {clientEndpoint}/login?provider={name}[&goto={url}]
                    checkRequestIsSufficientlySecure(exchange);
                    return handleUserInitiatedLogin(exchange, request);
                }
            }

            // Authorize call-back: {clientEndpoint}/callback?...
            if (matchesUri(exchange, buildCallbackUri(exchange))) {
                checkRequestIsSufficientlySecure(exchange);
                return handleAuthorizationCallback(exchange, request);
            }

            // Logout: {clientEndpoint}/logout[?goto={url}]
            if (matchesUri(exchange, buildLogoutUri(exchange))) {
                return handleUserInitiatedLogout(exchange, request);
            }

            // Everything else...
            return handleProtectedResource(exchange, request, next);
        } catch (final OAuth2ErrorException e) {
            return handleOAuth2ErrorException(exchange, request, e);
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
    }

    /**
     * Sets the expression which will be used for obtaining the base URI for the
     * following client end-points:
     * <ul>
     * <li><tt>{endpoint}/callback</tt> - called by the authorization server
     * once authorization has completed
     * <li>{@code {endpoint}/login?provider={name}[&goto={url}]} - user
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
    public OAuth2ClientFilter setClientEndpoint(final Expression<String> endpoint) {
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
    public OAuth2ClientFilter setDefaultLoginGoto(final Expression<String> endpoint) {
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
    public OAuth2ClientFilter setDefaultLogoutGoto(final Expression<String> endpoint) {
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
     * {@code
     * <target> : {
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
     * Sets the handler which will be invoked when discovery is activated. This
     * configuration parameter is required if discovery and dynamic registration
     * are needed.
     *
     * @param loginEndpoint
     *            The handler which will be invoked when the discovery is
     *            initiated.
     * @return This filter.
     */
    public OAuth2ClientFilter setLoginEndpoint(final Handler loginEndpoint) {
        this.loginEndpoint = loginEndpoint;
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
     * This configuration parameter is optional.
     *
     * @param scopes
     *            The expressions which will be used for obtaining the OAuth 2
     *            scopes.
     * @return This filter.
     */
    public OAuth2ClientFilter setScopes(final List<Expression<String>> scopes) {
        this.scopes = scopes != null ? scopes : Collections.<Expression<String>> emptyList();
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
    public OAuth2ClientFilter setTarget(final Expression<?> target) {
        this.target = target;
        return this;
    }

    private URI buildCallbackUri(final Exchange exchange) throws ResponseException {
        return buildUri(exchange, clientEndpoint, "callback");
    }

    private URI buildLoginUri(final Exchange exchange) throws ResponseException {
        return buildUri(exchange, clientEndpoint, "login");
    }

    private URI buildLogoutUri(final Exchange exchange) throws ResponseException {
        return buildUri(exchange, clientEndpoint, "logout");
    }

    private void checkRequestIsSufficientlySecure(final Exchange exchange)
            throws OAuth2ErrorException {
        // FIXME: use enforce filter?
        if (requireHttps && !"https".equalsIgnoreCase(exchange.getOriginalUri().getScheme())) {
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

    private OAuth2Provider getProvider(final OAuth2Session session) {
        final String providerName = session.getProviderName();
        return providerName != null ? providers.get(providerName) : null;
    }

    private List<String> getScopes(final Exchange exchange, final OAuth2Provider provider)
            throws ResponseException {
        final List<String> providerScopes = provider.getScopes(exchange);
        if (!providerScopes.isEmpty()) {
            return providerScopes;
        }
        return OAuth2Utils.getScopes(exchange, scopes);
    }

    private Promise<Response, NeverThrowsException> handleAuthorizationCallback(final Exchange exchange,
                                                                                final Request request)
            throws OAuth2ErrorException {

        try {
            if (!"GET".equals(request.getMethod())) {
                throw new OAuth2ErrorException(E_INVALID_REQUEST,
                        "Authorization call-back failed because the request was not a GET");
            }

            /*
             * The state must be valid regardless of whether the authorization
             * succeeded or failed.
             */
            final String state = request.getForm().getFirst("state");
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

            final String code = request.getForm().getFirst("code");
            if (code == null) {
                throw new OAuth2ErrorException(OAuth2Error.valueOfForm(request.getForm()));
            }

            JsonValue accessTokenResponse = null;
            final OAuth2Provider provider = getProvider(session);
            if (provider == null) {
                final ClientRegistration cr = heap.get(session.getProviderName(), ClientRegistration.class);
                if (cr == null) {
                    throw new OAuth2ErrorException(E_INVALID_REQUEST,
                            "Authorization call-back failed because the provider name was unrecognized");
                }
                accessTokenResponse = cr.getAccessToken(exchange, code, buildCallbackUri(exchange).toString());
            } else {
                accessTokenResponse = provider.getAccessToken(exchange, code, buildCallbackUri(exchange).toString());
            }

            /*
             * Finally complete the authorization request by redirecting to the
             * original goto URI and saving the session. It is important to save the
             * session after setting the response because it may need to access
             * response cookies.
             */
            final OAuth2Session authorizedSession = session.stateAuthorized(accessTokenResponse);
            return httpRedirectGoto(exchange, gotoUri, defaultLoginGoto)
                    .then(new Function<Response, Response, NeverThrowsException>() {
                        @Override
                        public Response apply(final Response response) {
                            try {
                                saveSession(exchange, authorizedSession);
                            } catch (ResponseException e) {
                                return e.getResponse();
                            }
                            return response;
                        }
                    });
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        } catch (HeapException e) {
            throw new OAuth2ErrorException(E_SERVER_ERROR,
                    "Cannot retrieve the appropriate Client Registration from the heap");
        }
    }

    private Promise<Response, NeverThrowsException> handleOAuth2ErrorException(final Exchange exchange,
                                                                               final Request request,
                                                                               final OAuth2ErrorException e) {
        final OAuth2Error error = e.getOAuth2Error();
        if (error.is(E_ACCESS_DENIED) || error.is(E_INVALID_TOKEN)) {
            logger.debug(e.getMessage());
        } else {
            // Assume all other errors are more serious operational errors.
            logger.warning(e.getMessage());
        }
        final Map<String, Object> info = new LinkedHashMap<>();
        try {
            final OAuth2Session session = loadOrCreateSession(exchange);
            info.putAll(session.getAccessTokenResponse());

            // Override these with effective values.
            info.put("provider", session.getProviderName());
            info.put("client_endpoint", session.getClientEndpoint());
            info.put("expires_in", session.getExpiresIn());
            info.put("scope", session.getScopes());
            final SignedJwt idToken = session.getIdToken();
            if (idToken != null) {
                final Map<String, Object> idTokenClaims = new LinkedHashMap<>();
                for (final String claim : idToken.getClaimsSet().keys()) {
                    idTokenClaims.put(claim, idToken.getClaimsSet().getClaim(claim));
                }
                info.put("id_token_claims", idTokenClaims);
            }
        } catch (Exception ignored) {
            /*
             * The session could not be decoded. Presumably this is why we are
             * here already, so simply ignore the error, and use the error that
             * was passed in to this method.
             */
        }
        info.put("error", error.toJsonContent());
        target.set(exchange, info);
        return failureHandler.handle(exchange, request);
    }

    private Promise<Response, NeverThrowsException> handleProtectedResource(final Exchange exchange,
                                                                            final Request request,
                                                                            final Handler next)
            throws OAuth2ErrorException {
        try {
            final OAuth2Session session = loadOrCreateSession(exchange);
            if (!session.isAuthorized() && requireLogin) {
                return sendRedirectForAuthorization(exchange, request);
            }
            final OAuth2Session refreshedSession =
                    session.isAuthorized() ? prepareExchange(exchange, session) : session;
            return next.handle(exchange, request)
                    .thenAsync(new AsyncFunction<Response, Response, NeverThrowsException>() {
                        @Override
                        public Promise<Response, NeverThrowsException> apply(final Response response) {
                            if (Status.UNAUTHORIZED.equals(response.getStatus()) && !refreshedSession.isAuthorized()) {
                                closeSilently(response);
                                return sendRedirectForAuthorization(exchange, request);
                            } else if (session != refreshedSession) {
                                /*
                                 * Only update the session if it has changed in order to avoid send
                                 * back JWT session cookies with every response.
                                 */
                                try {
                                    saveSession(exchange, refreshedSession);
                                } catch (ResponseException e) {
                                    return newResultPromise(e.getResponse());
                                }
                            }
                            return newResultPromise(response);
                        }
                    });
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedDiscovery(final Request request,
                                                                                 final Context context)
            throws OAuth2ErrorException, ResponseException {

        final Exchange exchange = context.asContext(Exchange.class);
        exchange.put("clientEndpoint", buildUri(exchange, clientEndpoint));
        return loginEndpoint.handle(context, request);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogin(final Exchange exchange,
                                                                             final Request request)
            throws OAuth2ErrorException {
        final String providerName = request.getForm().getFirst("provider");
        final String gotoUri = request.getForm().getFirst("goto");
        if (providerName == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization provider must be specified");
        }
        final OAuth2Provider provider = providers.get(providerName);
        if (provider == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST, "Authorization provider '"
                    + providerName + "' was not recognized");
        }
        return sendAuthorizationRedirect(exchange, request, provider, gotoUri);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogout(final Exchange exchange,
                                                                              final Request request)
            throws ResponseException {
        final String gotoUri = request.getForm().getFirst("goto");
        return httpRedirectGoto(exchange, gotoUri, defaultLogoutGoto)
                .then(new Function<Response, Response, NeverThrowsException>() {
                    @Override
                    public Response apply(final Response response) {
                        try {
                            removeSession(exchange);
                        } catch (ResponseException e) {
                            return e.getResponse();
                        }
                        return response;
                    }
                });
    }

    private Promise<Response, NeverThrowsException> httpRedirectGoto(final Exchange exchange,
                                                                     final String gotoUri,
                                                                     final Expression<String> defaultGotoUri) {
        try {
            if (gotoUri != null) {
                return completion(httpRedirect(gotoUri));
            } else if (defaultGotoUri != null) {
                return completion(httpRedirect(buildUri(exchange, defaultGotoUri).toString()));
            } else {
                return completion(httpResponse(Status.OK));
            }
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
    }

    private Promise<Response, NeverThrowsException> completion(Response response) {
        return newResultPromise(response);
    }

    private OAuth2Session prepareExchange(final Exchange exchange, final OAuth2Session session)
            throws ResponseException, OAuth2ErrorException {
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
                // The session is updated with new access token.
                final JsonValue accessTokenResponse = provider.getRefreshToken(exchange, session);
                final OAuth2Session refreshedSession = session.stateRefreshed(accessTokenResponse);
                tryPrepareExchange(exchange, refreshedSession);
                return refreshedSession;
            }

            /*
             * It looks like the token cannot be refreshed or something more
             * serious happened, e.g. the token has the wrong scopes. Re-throw
             * the error and let the failure-handler deal with it.
             */
            throw e;
        }
    }

    private Promise<Response, NeverThrowsException> sendAuthorizationRedirect(final Exchange exchange,
                                                                              final Request request,
                                                                              final OAuth2Provider provider,
                                                                              final String gotoUri) {
        try {
            final URI uri = provider.getAuthorizeEndpoint(exchange);
            final List<String> requestedScopes = getScopes(exchange, provider);
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
            return completion(httpRedirect(redirect))
                    .then(new Function<Response, Response, NeverThrowsException>() {
                        @Override
                        public Response apply(final Response response) {
                            /*
                             * Finally create and save the session. This may involve updating
                             * response cookies, so it is important to do it after creating the
                             * response.
                             */
                            try {
                                final String clientUri = buildUri(exchange, clientEndpoint).toString();
                                final OAuth2Session session =
                                        stateNew(time).stateAuthorizing(provider.getName(), clientUri, nonce,
                                                                        requestedScopes);
                                saveSession(exchange, session);
                                return response;
                            } catch (ResponseException e) {
                                return e.getResponse();
                            }
                        }
                    });

        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
    }

    private Promise<Response, NeverThrowsException> sendRedirectForAuthorization(final Exchange exchange,
                                                                                 final Request request) {
        if (loginHandler != null) {
            return loginHandler.handle(exchange, request);
        } else {
            final OAuth2Provider provider = providers.values().iterator().next();
            return sendAuthorizationRedirect(exchange, request, provider, exchange.getOriginalUri().toString());
        }
    }

    private String sessionKey(final Exchange exchange) throws ResponseException {
        return "oauth2:" + buildUri(exchange, clientEndpoint);
    }

    private void tryPrepareExchange(final Exchange exchange, final OAuth2Session session)
            throws ResponseException, OAuth2ErrorException {
        final Map<String, Object> info =
                new LinkedHashMap<>(session.getAccessTokenResponse());
        // Override these with effective values.
        info.put("provider", session.getProviderName());
        info.put("client_endpoint", session.getClientEndpoint());
        info.put("expires_in", session.getExpiresIn());
        info.put("scope", session.getScopes());
        final SignedJwt idToken = session.getIdToken();
        if (idToken != null) {
            final Map<String, Object> idTokenClaims = new LinkedHashMap<>();
            for (final String claim : idToken.getClaimsSet().keys()) {
                idTokenClaims.put(claim, idToken.getClaimsSet().getClaim(claim));
            }
            info.put("id_token_claims", idTokenClaims);
        }

        final OAuth2Provider provider = getProvider(session);
        if (provider != null
                && provider.hasUserInfoEndpoint()
                && session.getScopes().contains("openid")) {
            // Load the user_info resources lazily (when requested)
            info.put("user_info", new LazyMap<>(new UserInfoFactory(session,
                                                                    provider,
                                                                    exchange)));
        }
        target.set(exchange, info);
    }

    /**
     * Set the cache of user info resources. The cache is keyed by the OAuth 2.0 Access Token. It should be configured
     * with a small expiration duration (something between 5 and 30 seconds).
     *
     * @param userInfoCache
     *         the cache of user info resources.
     */
    public void setUserInfoCache(final ThreadSafeCache<String, Map<String, Object>> userInfoCache) {
        this.userInfoCache = userInfoCache;
    }

    /** Creates and initializes the filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private ScheduledExecutorService executor;
        private ThreadSafeCache<String, Map<String, Object>> cache;

        @Override
        public Object create() throws HeapException {

            TimeService time = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
            final OAuth2ClientFilter filter = new OAuth2ClientFilter(time, heap);

            filter.setTarget(asExpression(config.get("target").defaultTo(
                    format("${exchange.%s}", DEFAULT_TOKEN_KEY)), Object.class));
            filter.setScopes(config.get("scopes").defaultTo(emptyList()).asList(ofExpression()));
            filter.setClientEndpoint(asExpression(config.get("clientEndpoint").required(), String.class));
            final Handler loginEndpoint = heap.resolve(config.get("loginEndpoint"), Handler.class, true);
            filter.setLoginEndpoint(loginEndpoint);
            final Handler loginHandler = heap.resolve(config.get("loginHandler"), Handler.class, true);
            filter.setLoginHandler(loginHandler);
            filter.setFailureHandler(heap.resolve(config.get("failureHandler"),
                    Handler.class));
            filter.setDefaultLoginGoto(asExpression(config.get("defaultLoginGoto"), String.class));
            filter.setDefaultLogoutGoto(asExpression(config.get("defaultLogoutGoto"), String.class));
            filter.setRequireHttps(config.get("requireHttps").defaultTo(true).asBoolean());
            filter.setRequireLogin(config.get("requireLogin").defaultTo(true).asBoolean());

            int providerCount = 0;
            for (final JsonValue jv : config.get("providers").expect(List.class)) {
                final OAuth2Provider oauth2Provider = heap.resolve(jv, OAuth2Provider.class);
                filter.addProvider(oauth2Provider);
                providerCount++;
            }
            if (providerCount == 0) {
                throw new HeapException("At least one authorization provider must be specified");
            }
            if (loginHandler == null && providerCount > 1) {
                throw new HeapException(
                        "A login handler must be specified when there are multiple providers");
            }

            // Build the cache of user-info
            Duration expiration = duration(config.get("cacheExpiration").defaultTo("20 seconds").asString());
            if (!expiration.isZero()) {
                executor = Executors.newSingleThreadScheduledExecutor();
                cache = new ThreadSafeCache<>(executor);
                cache.setTimeout(expiration);
                filter.setUserInfoCache(cache);
            }

            return filter;
        }

        @Override
        public void destroy() {
            executor.shutdownNow();
            cache.clear();
        }
    }

    private OAuth2Session loadOrCreateSession(final Exchange exchange) throws OAuth2ErrorException,
                                                                              ResponseException {
        final Object sessionJson = exchange.getSession().get(sessionKey(exchange));
        if (sessionJson != null) {
            return OAuth2Session.fromJson(time, new JsonValue(sessionJson));
        }
        return stateNew(time);
    }

    private void removeSession(Exchange exchange) throws ResponseException {
        exchange.getSession().remove(sessionKey(exchange));
    }

    private void saveSession(Exchange exchange, OAuth2Session session) throws ResponseException {
        exchange.getSession().put(sessionKey(exchange), session.toJson().getObject());
    }

    /**
     * UserInfoFactory is responsible to load the profile of the authenticated user
     * from the provider's user_info endpoint when the lazy map is accessed for the first time.
     * If a cache has been configured
     */
    private class UserInfoFactory implements Factory<Map<String, Object>> {

        private final LoadUserInfoCallable callable;

        public UserInfoFactory(final OAuth2Session session,
                               final OAuth2Provider provider,
                               final Exchange exchange) {
            this.callable = new LoadUserInfoCallable(session, provider, exchange);
        }

        @Override
        public Map<String, Object> newInstance() {
            /*
             * When the 'user_info' attribute is accessed for the first time,
             * try to load the value (from the cache or not depending on the configuration).
             * The callable (factory for loading user info resource) will perform the appropriate HTTP request
             * to retrieve the user info as JSON, and then will return that content as a Map
             */

            if (userInfoCache == null) {
                // No cache is configured, go directly though the callable
                try {
                    return callable.call();
                } catch (Exception e) {
                    logger.warning(format("Unable to call UserInfo Endpoint from provider '%s'",
                                          callable.getProvider().getName()));
                    logger.warning(e);
                }
            } else {
                // A cache is configured, extract the value from the cache
                try {
                    return userInfoCache.getValue(callable.getSession().getAccessToken(),
                                                  callable);
                } catch (InterruptedException e) {
                    logger.warning(format("Interrupted when calling UserInfo Endpoint from provider '%s'",
                                          callable.getProvider().getName()));
                    logger.warning(e);
                } catch (ExecutionException e) {
                    logger.warning(format("Unable to call UserInfo Endpoint from provider '%s'",
                                          callable.getProvider().getName()));
                    logger.warning(e);
                }
            }

            // In case of errors, returns an empty Map
            return emptyMap();
        }
    }

    /**
     * LoadUserInfoCallable simply encapsulate the logic required to load the user_info resources.
     */
    private class LoadUserInfoCallable implements Callable<Map<String, Object>> {
        private final OAuth2Session session;
        private final OAuth2Provider provider;
        private final Exchange exchange;

        public LoadUserInfoCallable(final OAuth2Session session,
                                    final OAuth2Provider provider,
                                    final Exchange exchange) {
            this.session = session;
            this.provider = provider;
            this.exchange = exchange;
        }

        @Override
        public Map<String, Object> call() throws Exception {
            return provider.getUserInfo(exchange, session).asMap();
        }

        public OAuth2Session getSession() {
            return session;
        }

        public OAuth2Provider getProvider() {
            return provider;
        }
    }
}
