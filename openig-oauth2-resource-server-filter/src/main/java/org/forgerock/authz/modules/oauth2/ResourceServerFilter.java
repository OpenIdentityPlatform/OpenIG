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

package org.forgerock.authz.modules.oauth2;

import static org.forgerock.http.protocol.Response.newResponsePromise;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a {@link Request} that contains an OAuth 2.0 access token. <p> This filter expects an OAuth 2.0 token to
 * be available in the HTTP {@literal Authorization} header:
 *
 * <pre>{@code Authorization: Bearer 1fc0e143-f248-4e50-9c13-1d710360cec9}</pre>
 * <p>
 * It extracts the token and validate it against a token info endpoint using the provided {@link ResourceAccess}.
 * <p>
 * The provided {@link ResourceAccess} must provides the scopes required by the
 * {@link AccessToken} to access the protected resource.
 * <p>
 * Once the {@link AccessToken} is validated, it is stored in an {@link OAuth2Context} instance
 * which is forwarded with the {@link Request} to the next {@link Handler}.
 * The {@link AccessToken} could be retrieve in downstream handlers with {@link OAuth2Context#getAccessToken()}.
 * <p>
 * The {@literal realm} constructor attribute specifies the name of the realm used in the authentication challenges
 * returned back to the client in case of errors.
 */
public class ResourceServerFilter implements Filter {

    /** Authorization HTTP Header name. */
    static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String DESC_INVALID_TOKEN = "The access token provided is expired, revoked, "
            + "malformed, or invalid for other reasons.";

    private static final String DESC_INVALID_REQUEST = "The request is missing a required parameter, "
            + "includes an unsupported parameter or parameter value, repeats the same parameter, "
            + "uses more than one method for including an access token, or is otherwise malformed.";

    private static final String DESC_INSUFFICIENT_SCOPE =
            "The request requires higher privileges than provided by the access token.";

    private static Response notAuthorized(final String realm) {
        return newResourceServerErrorResponse(Status.UNAUTHORIZED, realm, null, null, null);
    }

    private static Response invalidRequest(final String realm, final AccessTokenException cause) {
        final Response response = newResourceServerErrorResponse(
                Status.BAD_REQUEST, realm, null, OAuth2Error.E_INVALID_REQUEST, DESC_INVALID_REQUEST);
        response.setCause(cause);
        return response;
    }

    private static Response invalidToken(final String realm) {
        return newResourceServerErrorResponse(
                Status.UNAUTHORIZED, realm, null, OAuth2Error.E_INVALID_TOKEN, DESC_INVALID_TOKEN);
    }

    private static Response insufficientScope(final String realm, final Set<String> scopes) {
        return newResourceServerErrorResponse(
                Status.FORBIDDEN, realm, scopes, OAuth2Error.E_INSUFFICIENT_SCOPE, DESC_INSUFFICIENT_SCOPE);
    }

    private static Response newResourceServerErrorResponse(final Status status, final String realm,
            final Set<String> scopes, final String error, final String errorDesc) {
        final Response response = new Response(status);
        final OAuth2Error oAuth2Error = OAuth2Error.newResourceServerError(
                realm, scopes == null ? null : new ArrayList<>(scopes), error, errorDesc, null);
        response.getHeaders().put(WWW_AUTHENTICATE_HEADER, oAuth2Error.toWWWAuthenticateHeader());
        return response;
    }

    private static final Logger logger = LoggerFactory.getLogger(ResourceServerFilter.class);

    private final AccessTokenResolver resolver;
    private final TimeService time;
    private final ResourceAccess resourceAccess;
    private final String realm;

    /**
     * Creates a new {@code OAuth2Filter}.
     *
     * @param resolver
     *         A {@code AccessTokenResolver} instance.
     * @param time
     *         A {@link TimeService} instance used to check if token is expired or not.
     * @param resourceAccess
     *         A {@link ResourceAccess} instance.
     * @param realm
     *         Name of the realm (used in authentication challenge returned in case of error).
     */
    public ResourceServerFilter(final AccessTokenResolver resolver,
                                final TimeService time,
                                final ResourceAccess resourceAccess,
                                final String realm) {
        this.resolver = resolver;
        this.time = time;
        this.resourceAccess = resourceAccess;
        this.realm = realm;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        final String token;
        try {
            token = getAccessToken(request);
            if (token == null) {
                logger.debug("Missing OAuth 2.0 Bearer Token in the Authorization header");
                return newResponsePromise(notAuthorized(realm));
            }
        } catch (AccessTokenException e) {
            logger.debug("Multiple 'Authorization' headers in the request", e);
            return newResponsePromise(invalidRequest(realm, e));
        }

        // Resolve the token
        return resolver.resolve(context, token)
                       .thenAsync(onResolverSuccess(context, request, next),
                                  onResolverException(token));
    }

    private AsyncFunction<AccessTokenException, Response, NeverThrowsException> onResolverException(
            final String token) {
        return new AsyncFunction<AccessTokenException, Response, NeverThrowsException>() {
            @Override
            public Promise<? extends Response, ? extends NeverThrowsException> apply(AccessTokenException e) {
                logger.debug("Access Token '{}' cannot be resolved", token, e);
                return newResponsePromise(invalidToken(realm));
            }
        };

    }

    private AsyncFunction<AccessToken, Response, NeverThrowsException> onResolverSuccess(final Context context,
                                                                                         final Request request,
                                                                                         final Handler next) {
        return new AsyncFunction<AccessToken, Response, NeverThrowsException>() {
            @Override
            public Promise<? extends Response, ? extends NeverThrowsException> apply(AccessToken accessToken) {
                // Validate the token (expiration + scopes)
                if (isExpired(accessToken)) {
                    logger.debug("Access Token {} is expired", accessToken);
                    return newResponsePromise(invalidToken(realm));
                }

                try {
                    final Set<String> scopesNeeded = resourceAccess.getRequiredScopes(context, request);
                    if (!accessToken.getScopes().containsAll(scopesNeeded)) {
                        logger.debug("Access Token {} is missing required scopes", accessToken);
                        return newResponsePromise(insufficientScope(realm, scopesNeeded));
                    }
                } catch (ResponseException e) {
                    return newResponsePromise(e.getResponse());
                }

                // Call the rest of the chain
                return next.handle(new OAuth2Context(context, accessToken), request);
            }
        };
    }

    private boolean isExpired(final AccessToken accessToken) {
        return time.now() > accessToken.getExpiresAt();
    }

    /**
     * Pulls the access token off of the request, by looking for the {@literal Authorization} header containing a
     * {@literal Bearer} token.
     *
     * @param request
     *         The Http {@link Request} message.
     * @return The access token, or {@literal null} if the access token was not present or was not using {@literal
     * Bearer} authorization.
     */
    private String getAccessToken(final Request request) throws AccessTokenException {
        Headers headers = request.getHeaders();
        Header authHeader = headers.get(AUTHORIZATION_HEADER);
        if (authHeader == null) {
            return null;
        }
        List<String> authorizations = authHeader.getValues();
        if (authorizations.size() > 1) {
            throw new AccessTokenException(
                    "Can't use more than 1 'Authorization' Header to convey the OAuth2 AccessToken");
        }
        return OAuth2.getBearerAccessToken(headers.getFirst("Authorization"));
    }
}
