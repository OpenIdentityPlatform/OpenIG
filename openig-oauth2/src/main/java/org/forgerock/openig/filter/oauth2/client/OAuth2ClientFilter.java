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
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.filter.RequestCopyFilter.requestCopyFilter;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.buildUri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.createAuthorizationNonceHash;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpRedirect;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.httpResponse;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.loadOrCreateSession;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.matchesUri;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.removeSession;
import static org.forgerock.openig.filter.oauth2.client.OAuth2Utils.saveSession;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.oauth2.OAuth2Error.E_INVALID_REQUEST;
import static org.forgerock.openig.oauth2.OAuth2Error.E_INVALID_TOKEN;
import static org.forgerock.openig.oauth2.OAuth2Error.E_SERVER_ERROR;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.getWithDeprecation;
import static org.forgerock.openig.util.JsonValues.optionalHeapObject;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.ZERO;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.jose.jws.SignedJwt;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.oauth2.OAuth2Error;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

/**
 * A filter which is responsible for authenticating the end-user using OAuth 2.0
 * delegated authorization. The filter does the following depending on the
 * incoming request URI:
 * <ul>
 * <li>{@code {clientEndpoint}/login?registration=<registrationName>&goto=<url>} - redirects
 * the user for authorization against the specified client
 * registration.
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
 * "target"                       : expression,             [OPTIONAL - default is ${attributes.openid}]
 * "clientEndpoint"               : expression,             [REQUIRED]
 * "loginHandler"                 : handler,                [REQUIRED - if zero or multiple client registrations.
 *                                                           OPTIONAL - if one client registration.]
 * "registrations"                : [ reference or          [OPTIONAL - MUST list the client registrations
 *                                    inlined declaration],             which are going to be used by this client.]
 * "discoveryHandler"             : handler,                [OPTIONAL - by default it uses the 'ClientHandler'
 *                                                                      provided in heap.]
 * "failureHandler"               : handler,                [REQUIRED]
 * "defaultLoginGoto"             : expression,             [OPTIONAL - default return empty page]
 * "defaultLogoutGoto"            : expression,             [OPTIONAL - default return empty page]
 * "requireLogin"                 : boolean                 [OPTIONAL - default require login]
 * "requireHttps"                 : boolean                 [OPTIONAL - default require SSL]
 * "cacheExpiration"              : duration                [OPTIONAL - default to 20 seconds]
 * "executor"                     : executor                [OPTIONAL - by default uses 'ScheduledThreadPool'
 *                                                                      heap object]
 * "metadata"                     : {                       [OPTIONAL - contains metadata dedicated for dynamic
 *                                                                      client registration.]
 *             "redirect_uris"    : [ strings ],                [REQUIRED for dynamic client registration.]
 *             "scopes"           : [ strings ]                 [OPTIONAL - usage with OpenAM only.]
 * }
 * }
 * </pre>
 *
 * For example, if you want to use a nascar page (with multiple client
 * registrations, defined in the "registrations" attribute):
 *
 * <pre>
 * {@code
 * {
 *     "name": "OpenIDConnect",
 *     "type": "OAuth2ClientFilter",
 *     "config": {
 *         "target"                : "${attributes.openid}",
 *         "clientEndpoint"        : "/openid",
 *         "registrations"         : [ "openam", "linkedin", "google" ],
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
 * This one, containing a nascar page and allowing dynamic client registration with OpenAM:
 *
 * <pre>
 * {@code
 * {
 *     "name": "OpenIDConnect",
 *     "type": "OAuth2ClientFilter",
 *     "config": {
 *         "target"                : "${attributes.openid}",
 *         "clientEndpoint"        : "/openid",
 *         "loginHandler"          : "NascarPage",
 *         "registrations"         : [ "openam", "linkedin", "google" ],
 *         "failureHandler"        : "LoginFailed",
 *         "defaultLoginGoto"      : "/homepage",
 *         "defaultLogoutGoto"     : "/loggedOut",
 *         "requireHttps"          : false,
 *         "requireLogin"          : true,
 *         "metadata"              : {
 *             "client_name": "iRock",
 *             "contacts": [ "werock@example.com", "werock@forgerock.org" ],
 *             "scopes": [
 *                 "openid", "profile"
 *             ],
 *             "redirect_uris": [ "http://my.example.com:8082/openid/callback" ],
 *             "logo_uri": "https://client.example.org/logo.png",
 *             "subject_type": "pairwise"
 *         }
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
 *         "target"                : "${attributes.openid}",
 *         "clientEndpoint"        : "/openid",
 *         "registrations"         : [ "openam" ],
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

    /** The expression which will be used for storing authorization information in the context. */
    public static final String DEFAULT_TOKEN_KEY = "openid";

    private Expression<String> clientEndpoint;
    private Expression<String> defaultLoginGoto;
    private Expression<String> defaultLogoutGoto;
    private Handler failureHandler;
    private Handler loginHandler;
    private boolean requireHttps = true;
    private boolean requireLogin = true;
    private Expression<?> target;
    private final TimeService time;
    private PerItemEvictionStrategyCache<String, Promise<Map<String, Object>, OAuth2ErrorException>> userInfoCache;
    private final Handler discoveryAndDynamicRegistrationChain;
    private final ClientRegistrationRepository registrations;

    /**
     * Constructs an {@link OAuth2ClientFilter}.
     *
     * @param registrations
     *            The {@link ClientRegistrationRepository} that handles the
     *            registrations.
     * @param userInfoCache
     *            The cache to store the user informations, not {@code null}.
     * @param time
     *            The TimeService to use.
     * @param discoveryAndDynamicRegistrationChain
     *            The chain used for discovery and dynamic client registration.
     * @param clientEndpoint
     *            The expression which will be used for obtaining the base URI
     *            for this filter.
     */
    public OAuth2ClientFilter(ClientRegistrationRepository registrations,
                              PerItemEvictionStrategyCache<String,
                                      Promise<Map<String, Object>, OAuth2ErrorException>> userInfoCache,
                              TimeService time,
                              Handler discoveryAndDynamicRegistrationChain,
                              Expression<String> clientEndpoint) {
        this.registrations = checkNotNull(registrations);
        this.userInfoCache = checkNotNull(userInfoCache);
        this.time = time;
        this.clientEndpoint = clientEndpoint;
        this.discoveryAndDynamicRegistrationChain = discoveryAndDynamicRegistrationChain;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        try {
            // Login: {clientEndpoint}/login
            UriRouterContext routerContext = context.asContext(UriRouterContext.class);
            URI originalUri = routerContext.getOriginalUri();
            if (matchesUri(originalUri, buildLoginUri(context, request))) {
                if (request.getForm().containsKey("discovery")) {
                    // User input: {clientEndpoint}/login?discovery={input}[&goto={url}]
                    return handleUserInitiatedDiscovery(request, context);
                } else {
                    // Login: {clientEndpoint}/login?registration={name}[&goto={url}]
                    checkRequestIsSufficientlySecure(context);
                    return handleUserInitiatedLogin(context, request);
                }
            }

            // Authorize call-back: {clientEndpoint}/callback?...
            if (matchesUri(originalUri, buildCallbackUri(context, request))) {
                checkRequestIsSufficientlySecure(context);
                return handleAuthorizationCallback(context, request);
            }

            // Logout: {clientEndpoint}/logout[?goto={url}]
            if (matchesUri(originalUri, buildLogoutUri(context, request))) {
                return handleUserInitiatedLogout(context, request);
            }

            // Everything else...
            return handleProtectedResource(context, request, next, true);
        } catch (final OAuth2ErrorException | ResponseException e) {
            return handleException(context, request, e);
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
     * the target expression will be populated with the following OAuth
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
     * information in the context. This configuration parameter is required.
     *
     * @param target
     *            The expression which will be used for storing authorization
     *            information in the context.
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

    private void checkRequestIsSufficientlySecure(final Context context)
            throws OAuth2ErrorException {
        // FIXME: use enforce filter?
        UriRouterContext routerContext = context.asContext(UriRouterContext.class);
        if (requireHttps && !"https".equalsIgnoreCase(routerContext.getOriginalUri().getScheme())) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "SSL is required in order to perform this operation");
        }
    }

    private ClientRegistration getClientRegistration(final OAuth2Session session) {
        final String name = session.getClientRegistrationName();
        return name != null ? registrations.findByName(name) : null;
    }

    private Promise<Response, NeverThrowsException> handleAuthorizationCallback(final Context context,
                                                                                final Request request)
            throws OAuth2ErrorException, ResponseException {

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

        final ClientRegistration client = getClientRegistration(session);
        if (client == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST, format(
                    "Authorization call-back failed because the client registration %s was unrecognized",
                    session.getClientRegistrationName()));
        }
        JsonValue accessTokenResponse;
        try {
            accessTokenResponse = safeGetOrThrow(client.getAccessToken(context,
                                                                       code,
                                                                       buildCallbackUri(context, request).toString()));
        } catch (InterruptedException ex) {
            throw new OAuth2ErrorException(E_SERVER_ERROR, "Interrupted while getting the access token", ex);
        }

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
                            logger.error("Unable to save the session on redirect");
                            logger.error(e);
                            return e.getResponse();
                        }
                        return response;
                    }
                });
    }

    private Promise<Response, NeverThrowsException> handleException(final Context context,
                                                                    final Request request,
                                                                    final Exception e) {
        Map<String, Object> info;
        try {
            info = extractSessionInfo(context, request);
        } catch (Exception ignored) {
            /*
             * The session could not be decoded. Presumably this is why we are
             * here already, so simply ignore the error, and use the error that
             * was passed in to this method.
             */
            info = new LinkedHashMap<String, Object>();
        }

        if (e instanceof OAuth2ErrorException) {
            final OAuth2Error error = ((OAuth2ErrorException) e).getOAuth2Error();
            logger.error(e.getMessage());
            info.put("error", error.toJsonContent());
        }
        info.put("exception", e);
        target.set(bindings(context, request), info);
        return failureHandler.handle(context, request);
    }

    private Promise<Response, NeverThrowsException> handleProtectedResource(final Context context,
                                                                            final Request request,
                                                                            final Handler next,
                                                                            final boolean refreshToken) {
        final OAuth2Session session;
        try {
            session = loadOrCreateSession(context, request, clientEndpoint, time);

            if (!session.isAuthorized() && requireLogin) {
                return sendAuthorizationRedirect(context, request, null);
            }
            if (session.isAuthorized()) {
                fillTarget(context, session, request);
            }
        } catch (OAuth2ErrorException | ResponseException e) {
            return handleException(context, request, e);
        }
        // Ensure we always work on a defensive copy of the request while executing the handler.
        final Handler handler = refreshToken ? chainOf(next, requestCopyFilter()) : next;
        final Promise<Response, NeverThrowsException> promise = handler.handle(context, request);
        if (refreshToken) {
            return promise.thenAsync(passThroughOrRefreshToken(context, request, handler, session));
        }
        return promise;
    }

    private AsyncFunction<Response, Response, NeverThrowsException> passThroughOrRefreshToken(
            final Context context,
            final Request request,
            final Handler next,
            final OAuth2Session session) {

        return new AsyncFunction<Response, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final Response response) {
                final Status status = response.getStatus();
                if (!(status.isServerError() || status.isClientError())) {
                    // Just forward the response as-is if not an error.
                    return newResultPromise(response);
                }
                if (!UNAUTHORIZED.equals(status)) {
                    return handleException(context, request, response.getCause());
                }

                final OAuth2Error error = OAuth2BearerWWWAuthenticateHeader.valueOf(response).getOAuth2Error();
                final ClientRegistration clientRegistration = getClientRegistration(session);
                if (!error.is(E_INVALID_TOKEN)
                        || (error.is(E_INVALID_TOKEN)
                                && (clientRegistration == null || session.getRefreshToken() == null))) {
                    // Unauthorized but not due to an invalid token, forwards it.
                    return handleException(context, request, response.getCause());
                }

                // At this point, we only react once to try to refresh the access token.
                logger.debug(format("The access token may have expired: %s", error.getErrorDescription()));
                return refreshAccessTokenAndSaveSession(context, request, session, clientRegistration)
                        .thenAsync(new AsyncFunction<Void, Response, NeverThrowsException>() {

                            @Override
                            public Promise<Response, NeverThrowsException> apply(Void value) {
                                // Try to access to the protected resource again with new access token.
                                return handleProtectedResource(context, request, next, false);
                            }
                        }, new AsyncFunction<OAuth2ErrorException, Response, NeverThrowsException>() {

                            @Override
                            public Promise<Response, NeverThrowsException> apply(OAuth2ErrorException e) {
                                // Couldn't refresh the access token: return the original response (401)
                                // to the caller.
                                logger.error(e);
                                return handleException(context, request, e);
                            }
                        });
            }

        };
    }

    private Promise<Void, OAuth2ErrorException> refreshAccessTokenAndSaveSession(
            final Context context,
            final Request request,
            final OAuth2Session session,
            final ClientRegistration clientRegistration) {

        return clientRegistration.refreshAccessToken(context, session).then(
                new Function<JsonValue, Void, OAuth2ErrorException>() {

                    @Override
                    public Void apply(JsonValue refreshedAccessTokenResponse) throws OAuth2ErrorException {
                        final OAuth2Session refreshedSession = session.stateRefreshed(refreshedAccessTokenResponse);
                        try {
                            saveSession(context, refreshedSession, buildUri(context, request, clientEndpoint));
                        } catch (final ResponseException e) {
                            throw new OAuth2ErrorException(E_SERVER_ERROR, "unable to save the session", e);
                        }
                        return null;
                    }
                });
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedDiscovery(final Request request,
                                                                                 final Context context) {

        return discoveryAndDynamicRegistrationChain.handle(context, request);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogin(final Context context,
                                                                             final Request request)
            throws OAuth2ErrorException {
        final String clientRegistrationName = request.getForm().getFirst("registration");
        if (clientRegistrationName == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST,
                    "Authorization OpenID Connect Provider must be specified");
        }
        final ClientRegistration clientRegistration = registrations.findByName(clientRegistrationName);
        if (clientRegistration == null) {
            throw new OAuth2ErrorException(E_INVALID_REQUEST, "Authorization OpenID Connect Provider '"
                    + clientRegistrationName + "' was not recognized");
        }
        return sendAuthorizationRedirect(context, request, clientRegistration);
    }

    private Promise<Response, NeverThrowsException> handleUserInitiatedLogout(final Context context,
                                                                              final Request request) {
        final String gotoUri = request.getForm().getFirst("goto");
        return httpRedirectGoto(context, request, gotoUri, defaultLogoutGoto)
                .then(new Function<Response, Response, NeverThrowsException>() {
                    @Override
                    public Response apply(final Response response) {
                        try {
                            removeSession(context, request, clientEndpoint);
                        } catch (ResponseException e) {
                            logger.error("Cannot remove the session on redirect");
                            logger.error(e);
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
                return completion(httpResponse(OK));
            }
        } catch (ResponseException e) {
            return handleException(context, request, e);
        }
    }

    private static Promise<Response, NeverThrowsException> completion(Response response) {
        return newResultPromise(response);
    }

    private Promise<Response, NeverThrowsException> sendAuthorizationRedirect(final Context context,
                                                                              final Request request,
                                                                              final ClientRegistration cr) {
        if (cr == null && loginHandler != null) {
            return loginHandler.handle(context, request);
        }
        return new AuthorizationRedirectHandler(time,
                                                clientEndpoint,
                                                cr != null ? cr : registrations.findDefault(),
                                                logger)
                                    .handle(context, request);
    }

    private void fillTarget(final Context context,
                            final OAuth2Session session,
                            final Request request) throws ResponseException, OAuth2ErrorException {
        final Map<String, Object> info;

        final ClientRegistration clientRegistration = getClientRegistration(session);
        if (clientRegistration != null
                && clientRegistration.getIssuer().hasUserInfoEndpoint()
                && session.getScopes().contains("openid")) {
            final Map<String, Object> userInfo = getUserInfo(context, session, request, clientRegistration);

            // Use the session info only after having fetched the user info: the session may have been refreshed.
            info = extractSessionInfo(context, request);
            info.put("user_info", userInfo);
        } else {
            info = extractSessionInfo(context, request);
        }
        target.set(bindings(context, null), info);
    }

    private Map<String, Object> extractSessionInfo(final Context context,
                                                   final Request request) throws ResponseException,
                                                                                 OAuth2ErrorException {
        final OAuth2Session session = OAuth2Utils.loadOrCreateSession(context, request, clientEndpoint, time);
        final Map<String, Object> info = new LinkedHashMap<>();
        info.putAll(session.getAccessTokenResponse());
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
        return info;
    }

    private Map<String, Object> getUserInfo(final Context context,
                                            final OAuth2Session session,
                                            final Request request,
                                            final ClientRegistration clientRegistration) throws OAuth2ErrorException {
        Map<String, Object> userInfo = null;
        try {
            Promise<Map<String, Object>, OAuth2ErrorException> promise =
                    userInfoCache.getValue(session.getAccessToken(),
                                           new LoadUserInfoCallable(session, clientRegistration, context, request));
            return safeGetOrThrow(promise);
        } catch (InterruptedException e) {
            logger.error(format("Interrupted when calling UserInfo Endpoint from client registration '%s'",
                    clientRegistration.getName()));
            logger.error(e);
        } catch (ExecutionException e) {
            logger.error(format("Unable to call UserInfo Endpoint from client registration '%s'",
                    clientRegistration.getName()));
            logger.error(e);
        }
        return userInfo;
    }

    /** Creates and initializes the filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private PerItemEvictionStrategyCache<String, Promise<Map<String, Object>, OAuth2ErrorException>> cache;

        @Override
        public Object create() throws HeapException {

            final Handler discoveryHandler = config.get("discoveryHandler")
                                                   .defaultTo(CLIENT_HANDLER_HEAP_KEY)
                                                   .as(requiredHeapObject(heap, Handler.class));
            TimeService time = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
            final Expression<String> clientEndpoint = config.get("clientEndpoint")
                                                            .required()
                                                            .as(expression(String.class));

            final List<ClientRegistration> clients = new LinkedList<>();
            final JsonValue regs = getWithDeprecation(config, logger, "registrations", "registration");
            if (regs.isNotNull()) {
                if (regs.isString() || regs.isMap()) {
                    clients.add(regs.as(requiredHeapObject(heap, ClientRegistration.class)));
                } else if (regs.isList()) {
                    clients.addAll(regs.as(listOf(requiredHeapObject(heap, ClientRegistration.class))));
                } else {
                    throw new HeapException("'registrations' must contains the name(s) or inlined declaration(s)"
                                            + "of the client registration's object(s) linked to this client");
                }
            }
            final ClientRegistrationRepository registrations = new HeapClientRegistrationRepository(clients,
                                                                                                    heap,
                                                                                                    logger);
            final Handler discoveryAndDynamicRegistrationChain = chainOf(
                    new AuthorizationRedirectHandler(time, clientEndpoint, logger),
                    new DiscoveryFilter(discoveryHandler, heap, logger),
                    new ClientRegistrationFilter(registrations, discoveryHandler, config.get("metadata"), logger));

            // Build the cache of user-info
            final Duration expiration = config.get("cacheExpiration")
                                              .as(evaluated())
                                              .defaultTo("20 seconds")
                                              .as(duration());
            ScheduledExecutorService executor = config.get("executor").defaultTo(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY)
                    .as(requiredHeapObject(heap, ScheduledExecutorService.class));

            cache = new PerItemEvictionStrategyCache<>(executor, expirationFunction(expiration));
            final OAuth2ClientFilter filter = new OAuth2ClientFilter(registrations,
                                                                     cache,
                                                                     time,
                                                                     discoveryAndDynamicRegistrationChain,
                                                                     clientEndpoint);

            filter.setTarget(config.get("target").defaultTo(format("${attributes.%s}", DEFAULT_TOKEN_KEY))
                                   .as(expression(Object.class)));

            final Handler loginHandler = config.get("loginHandler").as(optionalHeapObject(heap, Handler.class));
            filter.setLoginHandler(loginHandler);

            if (registrations.needsNascarPage() && loginHandler == null) {
                throw new HeapException("A 'loginHandler' (defining a NASCAR page) is required when there are zero"
                                                + " or multiple client registrations.");
            }
            filter.setFailureHandler(config.get("failureHandler").as(requiredHeapObject(heap, Handler.class)));
            filter.setDefaultLoginGoto(config.get("defaultLoginGoto").as(expression(String.class)));
            filter.setDefaultLogoutGoto(config.get("defaultLogoutGoto").as(expression(String.class)));
            filter.setRequireHttps(config.get("requireHttps").as(evaluated()).defaultTo(true).asBoolean());
            filter.setRequireLogin(config.get("requireLogin").as(evaluated()).defaultTo(true).asBoolean());

            return filter;
        }

        @VisibleForTesting
        static AsyncFunction<Promise<Map<String, Object>, OAuth2ErrorException>, Duration, Exception>
        expirationFunction(final Duration expiration) {
            return new AsyncFunction<Promise<Map<String, Object>, OAuth2ErrorException>, Duration, Exception>() {
                @Override
                public Promise<? extends Duration, ? extends OAuth2ErrorException> apply(Promise<Map<String, Object>,
                        OAuth2ErrorException> value) throws Exception {
                    return value.thenAsync(
                            new AsyncFunction<Map<String, Object>, Duration, OAuth2ErrorException>() {

                                private final Promise<Duration, OAuth2ErrorException> onResultDuration =
                                        newResultPromise(expiration);

                                @Override
                                public Promise<? extends Duration, ? extends OAuth2ErrorException> apply(
                                        Map<String, Object> value)
                                        throws OAuth2ErrorException {
                                    return onResultDuration;
                                }
                            },
                            new AsyncFunction<OAuth2ErrorException, Duration, OAuth2ErrorException>() {

                                private final Promise<Duration, OAuth2ErrorException> onExceptionDuration =
                                        newResultPromise(ZERO);

                                @Override
                                public Promise<? extends Duration, ? extends OAuth2ErrorException> apply(
                                        OAuth2ErrorException value)
                                        throws OAuth2ErrorException {
                                    return onExceptionDuration;
                                }
                            });
                }
            };
        }

        @Override
        public void destroy() {
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * LoadUserInfoCallable simply encapsulate the logic required to load the user_info resources.
     */
    private class LoadUserInfoCallable implements Callable<Promise<Map<String, Object>, OAuth2ErrorException>> {
        private OAuth2Session session;
        private final ClientRegistration clientRegistration;
        private final Context context;
        private final Request request;

        public LoadUserInfoCallable(final OAuth2Session session, final ClientRegistration clientRegistration,
                final Context context, final Request request) {
            this.session = session;
            this.clientRegistration = clientRegistration;
            this.context = context;
            this.request = request;
        }

        @Override
        public Promise<Map<String, Object>, OAuth2ErrorException> call() throws Exception {
            return clientRegistration.getUserInfo(context, session)
                                     .thenCatchAsync(onOAuth2ErrorException())
                                     .then(transformJsonValueToMap(), rethrowException());
        }

        private Function<OAuth2ErrorException, Map<String, Object>, OAuth2ErrorException> rethrowException() {
            return new Function<OAuth2ErrorException, Map<String, Object>, OAuth2ErrorException>() {
                @Override
                public Map<String, Object> apply(OAuth2ErrorException e) throws OAuth2ErrorException {
                    throw e;
                }
            };
        }

        private Function<JsonValue, Map<String, Object>, OAuth2ErrorException> transformJsonValueToMap() {
            return new Function<JsonValue, Map<String, Object>, OAuth2ErrorException>() {
                @Override
                public Map<String, Object> apply(JsonValue jsonValue) throws OAuth2ErrorException {
                    return jsonValue.asMap();
                }
            };
        }

        private AsyncFunction<OAuth2ErrorException, JsonValue, OAuth2ErrorException> onOAuth2ErrorException() {
            return new AsyncFunction<OAuth2ErrorException, JsonValue, OAuth2ErrorException>() {

                @Override
                public Promise<JsonValue, OAuth2ErrorException> apply(OAuth2ErrorException e)
                        throws OAuth2ErrorException {
                    final OAuth2Error error = e.getOAuth2Error();
                    if (error.is(E_INVALID_TOKEN) && session.getRefreshToken() != null) {
                        // Supposed expired token, try to update it by
                        // generating a new access token.
                        return updateSessionStateWithRefreshTokenOrFailWithNewSession();
                    }
                    return Promises.newExceptionPromise(e);
                }
            };
        }

        private Promise<JsonValue, OAuth2ErrorException> updateSessionStateWithRefreshTokenOrFailWithNewSession() {
            final URI uri;
            try {
                uri = buildUri(context, request, clientEndpoint);
            } catch (ResponseException ex1) {
                OAuth2ErrorException e = new OAuth2ErrorException(OAuth2Error.E_SERVER_ERROR,
                        "Unable to build the client endpoint URI", ex1);
                return Promises.newExceptionPromise(e);
            }

            return clientRegistration.refreshAccessToken(context, session).thenAsync(
                    new AsyncFunction<JsonValue, JsonValue, OAuth2ErrorException>() {

                        @Override
                        public Promise<JsonValue, OAuth2ErrorException> apply(JsonValue refreshAccessToken)
                                throws OAuth2ErrorException {
                            session = session.stateRefreshed(refreshAccessToken);
                            saveSession(context, session, uri);
                            return clientRegistration.getUserInfo(context, session);
                        }
                    }, new AsyncFunction<OAuth2ErrorException, JsonValue, OAuth2ErrorException>() {

                        @Override
                        public Promise<JsonValue, OAuth2ErrorException> apply(OAuth2ErrorException ex)
                                throws OAuth2ErrorException {
                            logger.error("Fail to refresh OAuth2 Access Token");
                            logger.error(ex);
                            session = OAuth2Session.stateNew(time);
                            saveSession(context, session, uri);
                            return Promises.newExceptionPromise(ex);
                        }
                    });
        }
    }

    private static <V, E extends Exception> V blockingCall(Promise<V, E> promise, String message)
            throws E, OAuth2ErrorException {
        try {
            return safeGetOrThrow(promise);
        } catch (InterruptedException e) {
            // TODO Remove the getOrThrow()
            throw new OAuth2ErrorException(E_SERVER_ERROR, "Interrupted while " + message, e);
        }
    }

    private static <V, E extends Exception> V safeGetOrThrow(Promise<V, E> promise) throws InterruptedException, E {
        final CountDownLatch latch = new CountDownLatch(1);
        V value = promise
                // Decrement the latch at the very end of the listener's sequence
                .thenAlways(new Runnable() {
                    @Override
                    public void run() {
                        latch.countDown();
                    }
                })
                // Block the promise, waiting for the response
                .getOrThrow();

        // Wait for the latch to be released so we can make sure that all of the Promise's ResultHandlers
        // and Functions have been invoked
        latch.await();
        return value;
    }
}
