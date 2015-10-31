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
import static java.util.Collections.emptyMap;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_ACCESS_DENIED;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_INVALID_REQUEST;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Error.E_INVALID_TOKEN;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.buildUri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.createAuthorizationNonceHash;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpRedirect;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpResponse;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.loadOrCreateSession;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.matchesUri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.removeSession;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.saveSession;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.JsonValues.getWithDeprecation;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.oauth2.cache.ThreadSafeCache;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.Context;
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
 * <li>{@code {clientEndpoint}/login/{registration}?goto=<url>} - redirects
 * the user for authorization against the specified client
 * registration
 * <li>{@code {clientEndpoint}/login?{*}discovery={input}&goto=<url>} -
 * performs issuer discovery and dynamic client registration if possible on
 * the given user input and redirects the user to the client endpoint.
 * <li>{@code {clientEndpoint}/logout?goto=<url>} - removes
 * authorization state for the end-user
 * <li>{@code {clientEndpoint}/callback} - OAuth 2.0 authorization
 * call-back end-point (state encodes nonce, goto, and client registration)
 * <li>all other requests - restores authorization state and places it in the
 * target location.
 * </ul>
 * <p>
 * Configuration options:
 *
 * <pre>
 * {@code
 * "target"                       : expression,         [OPTIONAL - default is ${contexts.attributes.attributes.openid}]
 * "clientEndpoint"               : expression,         [REQUIRED]
 * "loginHandler"                 : handler,            [REQUIRED - if multiple client registrations]
 * OR
 * "registration"                 : reference or        [REQUIRED - if you want to use a single client
 *                                  inlined declaration,            registration]
 * "discoveryHandler"             : handler,            [OPTIONAL - by default it uses the 'ClientHandler'
 *                                                                  provided in heap.]
 * "failureHandler"               : handler,            [REQUIRED]
 * "defaultLoginGoto"             : expression,         [OPTIONAL - default return empty page]
 * "defaultLogoutGoto"            : expression,         [OPTIONAL - default return empty page]
 * "requireLogin"                 : boolean             [OPTIONAL - default require login]
 * "requireHttps"                 : boolean             [OPTIONAL - default require SSL]
 * "cacheExpiration"              : duration            [OPTIONAL - default to 20 seconds]
 * }
 * </pre>
 *
 * For example, if you want to use a nascar page (multiple client
 * registrations):
 *
 * <pre>
 * {@code
 * {
 *     "name": "OpenIDConnect",
 *     "type": "OAuth2ClientFilter",
 *     "config": {
 *         "target"                : "${contexts.attributes.attributes.openid}",
 *         "clientEndpoint"        : "/openid",
 *         "loginHandler"          : "NascarPage",
 *         "failureHandler"        : "LoginFailed",
 *         "defaultLoginGoto"      : "/homepage",
 *         "defaultLogoutGoto"     : "/loggedOut",
 *         "requireHttps"          : false,
 *         "requireLogin"          : true
 *     }
 * }
 * }
 * </pre>
 *
 * Or this one, with a single client registration.
 *
 * <pre>
 * {@code
 * {
 *     "name": "OpenIDConnect",
 *     "type": "OAuth2ClientFilter",
 *     "config": {
 *         "target"                : "${contexts.attributes.attributes.openid}",
 *         "clientEndpoint"        : "/openid",
 *         "registration"          : "openam",
 *         "failureHandler"        : "LoginFailed"
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
 *         "client_registration" : "google",
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
    private final Handler discoveryHandler;
    private Handler failureHandler;
    private Handler loginHandler;
    private ClientRegistration clientRegistration;
    private boolean requireHttps = true;
    private boolean requireLogin = true;
    private Expression<?> target;
    private final TimeService time;
    private ThreadSafeCache<String, Map<String, Object>> userInfoCache;
    private final Heap heap;
    private final Handler discoveryAndDynamicRegistrationChain;

    /**
     * Constructs an {@link OAuth2ClientFilter}.
     *
     * @param time
     *            The TimeService to use.
     * @param heap
     *            The current heap.
     * @param config
     *            The json configuration of this filter.
     * @param name
     *            The name of this filter.
     * @param discoveryHandler
     *            The handler used for discovery and dynamic client
     *            registration.
     * @param clientEndpoint
     *            The expression which will be used for obtaining the base URI
     *            for this filter.
     */
    public OAuth2ClientFilter(TimeService time,
                              Heap heap,
                              JsonValue config,
                              String name,
                              Handler discoveryHandler,
                              Expression<String> clientEndpoint) {
        this.time = time;
        this.heap = heap;
        this.discoveryHandler = discoveryHandler;
        this.clientEndpoint = clientEndpoint;
        discoveryAndDynamicRegistrationChain = buildDiscoveryAndDynamicRegistrationChain(heap, config, name);
    }

    private Handler buildDiscoveryAndDynamicRegistrationChain(final Heap heap,
                                                              final JsonValue config,
                                                              final String name) {
        return chainOf(new AuthorizationRedirectHandler(time, clientEndpoint),
                       new DiscoveryFilter(discoveryHandler, heap),
                       new ClientRegistrationFilter(discoveryHandler, config, heap, name));
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        Exchange exchange = context.asContext(Exchange.class);
        try {
            // Login: {clientEndpoint}/login
            if (matchesUri(exchange.getOriginalUri(), buildLoginUri(context, request))) {
                if (request.getForm().containsKey("discovery")) {
                    // User input: {clientEndpoint}/login?discovery={input}[&goto={url}]
                    return handleUserInitiatedDiscovery(request, context);
                } else {
                    // Login: {clientEndpoint}/login?registration={name}[&goto={url}]
                    checkRequestIsSufficientlySecure(exchange);
                    return handleUserInitiatedLogin(context, request);
                }
            }

            // Authorize call-back: {clientEndpoint}/callback?...
            if (matchesUri(exchange.getOriginalUri(), buildCallbackUri(context, request))) {
                checkRequestIsSufficientlySecure(exchange);
                return handleAuthorizationCallback(context, request);
            }

            // Logout: {clientEndpoint}/logout[?goto={url}]
            if (matchesUri(exchange.getOriginalUri(), buildLogoutUri(context, request))) {
                return handleUserInitiatedLogout(context, request);
            }

            // Everything else...
            return handleProtectedResource(context, request, next);
        } catch (final OAuth2ErrorException e) {
            return handleOAuth2ErrorException(context, request, e);
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
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
     *     "client_registration" : "google",
     *     "error"               : {
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
     * Sets the handler which will be invoked when the user needs to
     * authenticate. This configuration parameter is required if there are more
     * than one client registration configured.
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
     * You can avoid a nascar page by setting a client registration.
     *
     * @param registration
     *            The client registration to use.
     * @return This filter.
     */
    public OAuth2ClientFilter setClientRegistration(final ClientRegistration registration) {
        this.clientRegistration = registration;
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

    private URI buildCallbackUri(final Context context, final Request request) throws ResponseException {
        return buildUri(context, request, clientEndpoint, "callback");
    }

    private URI buildLoginUri(final Context context, final Request request) throws ResponseException {
        return buildUri(context, request, clientEndpoint, "login");
    }

    private URI buildLogoutUri(final Context context, final Request request) throws ResponseException {
        return buildUri(context, request, clientEndpoint, "logout");
    }

    private void checkRequestIsSufficientlySecure(final Exchange exchange)
            throws OAuth2ErrorException {
        // FIXME: use enforce filter?
        if (requireHttps && !"https".equalsIgnoreCase(exchange.getOriginalUri().getScheme())) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "SSL is required in order to perform this operation");
        }
    }

    private ClientRegistration getClientRegistration(final OAuth2Session session) {
        final String name = session.getClientRegistrationName();
        return name != null ? getClientRegistrationFromHeap(name) : null;
    }

    private Promise<Response, NeverThrowsException> handleAuthorizationCallback(final Context context,
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
            final OAuth2Session session = loadOrCreateSession(context, request, clientEndpoint, time);
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

            final ClientRegistration client = getClientRegistrationFromHeap(session.getClientRegistrationName());
            if (client == null) {
                throw new OAuth2ErrorException(E_INVALID_REQUEST, format(
                        "Authorization call-back failed because the client registration %s was unrecognized",
                        session.getClientRegistrationName()));
            }
            final JsonValue accessTokenResponse = client.getAccessToken(context,
                                                                        code,
                                                                        buildCallbackUri(context, request).toString());

            /*
             * Finally complete the authorization request by redirecting to the
             * original goto URI and saving the session. It is important to save the
             * session after setting the response because it may need to access
             * response cookies.
             */
            final OAuth2Session authorizedSession = session.stateAuthorized(accessTokenResponse);
            return httpRedirectGoto(context, request, gotoUri, defaultLoginGoto)
                    .then(new Function<Response, Response, NeverThrowsException>() {
                        @Override
                        public Response apply(final Response response) {
                            try {
                                saveSession(context, authorizedSession, buildUri(context, request, clientEndpoint));
                            } catch (ResponseException e) {
                                return e.getResponse();
                            }
                            return response;
                        }
                    });
        } catch (ResponseException e) {
            return newResultPromise(e.getResponse());
        }
    }

    private Promise<Response, NeverThrowsException> handleOAuth2ErrorException(final Context context,
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
            final OAuth2Session session = loadOrCreateSession(context, request, clientEndpoint, time);
            info.putAll(session.getAccessTokenResponse());

            // Override these with effective values.
            info.put("clientRegistration", session.getClientRegistrationName());
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
        target.set(bindings(context, request), info);
        return failureHandler.handle(context, request);
    }

    private Promise<Response, NeverThrowsException> handleProtectedResource(final Context context,
                                                                            final Request request,
                                                                            final Handler next)
            throws OAuth2ErrorException {
        try {
            final OAuth2Session session = loadOrCreateSession(context, request, clientEndpoint, time);
            if (!session.isAuthorized() && requireLogin) {
                return sendAuthorizationRedirect(context, request, null);
            }
            final OAuth2Session refreshedSession =
                    session.isAuthorized() ? prepareExchange(context, session) : session;
            return next.handle(context, request)
                    .thenAsync(new AsyncFunction<Response, Response, NeverThrowsException>() {
                        @Override
                        public Promise<Response, NeverThrowsException> apply(final Response response) {
                            if (Status.UNAUTHORIZED.equals(response.getStatus()) && !refreshedSession.isAuthorized()) {
                                closeSilently(response);
                                return sendAuthorizationRedirect(context, request, null);
                            } else if (session != refreshedSession) {
                                /*
                                 * Only update the session if it has changed in order to avoid send
                                 * back JWT session cookies with every response.
                                 */
                                try {
                                    saveSession(context,
                                                refreshedSession,
                                                buildUri(context, request, clientEndpoint));
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

        return discoveryAndDynamicRegistrationChain.handle(context, request);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogin(final Context context,
                                                                             final Request request)
            throws OAuth2ErrorException, ResponseException {
        final String clientRegistrationName = request.getForm().getFirst("registration");
        if (clientRegistrationName == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization OpenID Connect Provider must be specified");
        }
        final ClientRegistration clientRegistration = getClientRegistrationFromHeap(clientRegistrationName);
        if (clientRegistration == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST, "Authorization OpenID Connect Provider '"
                    + clientRegistrationName + "' was not recognized");
        }
        return sendAuthorizationRedirect(context, request, clientRegistration);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogout(final Context context,
                                                                              final Request request)
            throws ResponseException {
        final String gotoUri = request.getForm().getFirst("goto");
        return httpRedirectGoto(context, request, gotoUri, defaultLogoutGoto)
                .then(new Function<Response, Response, NeverThrowsException>() {
                    @Override
                    public Response apply(final Response response) {
                        try {
                            removeSession(context, request, clientEndpoint);
                        } catch (ResponseException e) {
                            return e.getResponse();
                        }
                        return response;
                    }
                });
    }

    private Promise<Response, NeverThrowsException> httpRedirectGoto(final Context context,
                                                                     final Request request,
                                                                     final String gotoUri,
                                                                     final Expression<String> defaultGotoUri) {
        try {
            if (gotoUri != null) {
                return completion(httpRedirect(gotoUri));
            } else if (defaultGotoUri != null) {
                return completion(httpRedirect(buildUri(context, request, defaultGotoUri).toString()));
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

    private OAuth2Session prepareExchange(final Context context, final OAuth2Session session)
            throws ResponseException, OAuth2ErrorException {
        try {
            tryPrepareExchange(context, session);
            return session;
        } catch (final OAuth2ErrorException e) {
            /*
             * Try again if the access token looks like it has expired and can
             * be refreshed.
             */
            final OAuth2Error error = e.getOAuth2Error();
            final ClientRegistration clientRegistration = getClientRegistration(session);
            if (error.is(E_INVALID_TOKEN) && clientRegistration != null && session.getRefreshToken() != null) {
                // The session is updated with new access token.
                final JsonValue accessTokenResponse = clientRegistration.refreshAccessToken(context, session);
                final OAuth2Session refreshedSession = session.stateRefreshed(accessTokenResponse);
                tryPrepareExchange(context, refreshedSession);
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

    private Promise<Response, NeverThrowsException> sendAuthorizationRedirect(final Context context,
                                                                              final Request request,
                                                                              final ClientRegistration cr) {
        if (cr == null && loginHandler != null) {
            return loginHandler.handle(context, request);
        }
        return new AuthorizationRedirectHandler(time,
                                                clientEndpoint,
                                                cr != null ? cr : clientRegistration)
                                    .handle(context, request);
    }

    private ClientRegistration getClientRegistrationFromHeap(final String name) {
        ClientRegistration clientRegistration = null;
        try {
            clientRegistration = heap.get(name, ClientRegistration.class);
        } catch (HeapException e) {
            logger.error(format("Cannot retrieve the client registration '%s' from the heap", name));
        }
        return clientRegistration;
    }

    private void tryPrepareExchange(final Context context, final OAuth2Session session)
            throws ResponseException, OAuth2ErrorException {
        final Map<String, Object> info =
                new LinkedHashMap<>(session.getAccessTokenResponse());
        // Override these with effective values.
        info.put("client_registration", session.getClientRegistrationName());
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

        final ClientRegistration clientRegistration = getClientRegistration(session);
        if (clientRegistration != null
                && clientRegistration.getIssuer().hasUserInfoEndpoint()
                && session.getScopes().contains("openid")) {
            // Load the user_info resources lazily (when requested)
            info.put("user_info", new LazyMap<>(new UserInfoFactory(session,
                                                                    clientRegistration,
                                                                    context)));
        }
        final Exchange exchange = context.asContext(Exchange.class);
        target.set(bindings().bind("exchange", exchange), info);
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

            final Handler discoveryHandler = heap.resolve(config.get("discoveryHandler")
                                                                .defaultTo(CLIENT_HANDLER_HEAP_KEY),
                                                          Handler.class);
            TimeService time = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
            final Expression<String> clientEndpoint = asExpression(config.get("clientEndpoint").required(),
                                                                   String.class);
            final OAuth2ClientFilter filter = new OAuth2ClientFilter(time,
                                                                     heap,
                                                                     config,
                                                                     this.name,
                                                                     discoveryHandler,
                                                                     clientEndpoint);

            filter.setTarget(asExpression(config.get("target").defaultTo(
                    format("${contexts.attributes.attributes.%s}", DEFAULT_TOKEN_KEY)), Object.class));
            final JsonValue registration = getWithDeprecation(config, logger, "registration", "clientRegistrationName");
            if (registration.isNotNull()) {
                filter.setClientRegistration(heap.resolve(registration.required(),
                                                          ClientRegistration.class));
            } else {
                final Handler loginHandler = heap.resolve(config.get("loginHandler").required(), Handler.class, true);
                filter.setLoginHandler(loginHandler);
            }
            filter.setFailureHandler(heap.resolve(config.get("failureHandler"),
                    Handler.class));
            filter.setDefaultLoginGoto(asExpression(config.get("defaultLoginGoto"), String.class));
            filter.setDefaultLogoutGoto(asExpression(config.get("defaultLogoutGoto"), String.class));
            filter.setRequireHttps(config.get("requireHttps").defaultTo(true).asBoolean());
            filter.setRequireLogin(config.get("requireLogin").defaultTo(true).asBoolean());
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

    /**
     * UserInfoFactory is responsible to load the profile of the authenticated user
     * from the OpenID Connect Provider's user_info endpoint when the lazy map is accessed for the first time.
     * If a cache has been configured
     */
    private class UserInfoFactory implements Factory<Map<String, Object>> {

        private final LoadUserInfoCallable callable;

        public UserInfoFactory(final OAuth2Session session,
                               final ClientRegistration clientRegistration,
                               final Context context) {
            this.callable = new LoadUserInfoCallable(session, clientRegistration, context);
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
                    logger.warning(format("Unable to call UserInfo Endpoint from client registration '%s'",
                                          callable.getClientRegistration().getName()));
                    logger.warning(e);
                }
            } else {
                // A cache is configured, extract the value from the cache
                try {
                    return userInfoCache.getValue(callable.getSession().getAccessToken(),
                                                  callable);
                } catch (InterruptedException e) {
                    logger.warning(format("Interrupted when calling UserInfo Endpoint from client registration '%s'",
                                          callable.getClientRegistration().getName()));
                    logger.warning(e);
                } catch (ExecutionException e) {
                    logger.warning(format("Unable to call UserInfo Endpoint from client registration '%s'",
                                          callable.getClientRegistration().getName()));
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
        private final ClientRegistration clientRegistration;
        private final Context context;

        public LoadUserInfoCallable(final OAuth2Session session,
                                    final ClientRegistration clientRegistration,
                                    final Context context) {
            this.session = session;
            this.clientRegistration = clientRegistration;
            this.context = context;
        }

        @Override
        public Map<String, Object> call() throws Exception {
            return clientRegistration.getUserInfo(context, session).asMap();
        }

        public OAuth2Session getSession() {
            return session;
        }

        public ClientRegistration getClientRegistration() {
            return clientRegistration;
        }
    }
}
