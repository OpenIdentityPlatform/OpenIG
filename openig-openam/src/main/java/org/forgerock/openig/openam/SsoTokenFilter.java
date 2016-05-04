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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Provides an OpenAM SSO Token in the given header name for downstream components.
 *
 * <p>If the request failed with a {@literal 401} UNAUTHORIZED, a unique attempt to refresh the SSO token is tried.
 *
 * @see <a href="https://forgerock.org/openam/doc/bootstrap/dev-guide/index.html#rest-api-status-codes">OPENAM REST
 * API status codes</a>
 */
public class SsoTokenFilter implements Filter {

    static final String SSO_TOKEN_KEY = "SSOToken";
    static final String BASE_ENDPOINT = "json";
    static final String AUTHENTICATION_ENDPOINT = "/authenticate";
    static final String DEFAULT_HEADER_NAME = "iPlanetDirectoryPro";

    private final URI openamUrl;
    private final String realm;
    private final String headerName;
    private final Expression<String> username;
    private final Expression<String> password;
    private final Logger logger;
    private final SsoTokenHolder ssoTokenHolder;

    SsoTokenFilter(final Handler ssoClientHandler,
                   final URI openamUrl,
                   final String realm,
                   final String headerName,
                   final Expression<String> username,
                   final Expression<String> password,
                   final Logger logger) {
        this.openamUrl = checkNotNull(openamUrl);
        this.realm = startsWithSlash(realm);
        this.headerName = headerName != null ? headerName : DEFAULT_HEADER_NAME;
        this.username = username;
        this.password = password;
        this.logger = logger;
        ssoTokenHolder = new SsoTokenHolder(checkNotNull(ssoClientHandler, "The ssoClientHandler cannot be null"),
                                            logger);
    }

    private static String startsWithSlash(final String realm) {
        String nonNullRealm = realm != null ? realm : "/";
        return nonNullRealm.startsWith("/") ? nonNullRealm : "/" + nonNullRealm;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        final AsyncFunction<String, Response, NeverThrowsException> executeRequestWithToken =
                new AsyncFunction<String, Response, NeverThrowsException>() {

                    @Override
                    public Promise<Response, NeverThrowsException> apply(String token) {
                        if (token != null) {
                            request.getHeaders().put(headerName, token);
                            return next.handle(context, request);
                        } else {
                            logger.error("Unable to retrieve SSO Token");
                            return newResponsePromise(newInternalServerError());
                        }
                    }
                };

        final AsyncFunction<Response, Response, NeverThrowsException> checkResponse =
                new AsyncFunction<Response, Response, NeverThrowsException>() {

                    @Override
                    public Promise<Response, NeverThrowsException> apply(Response response) {
                        if (response.getStatus().equals(UNAUTHORIZED)) {
                            return ssoTokenHolder.updateToken(context, request).thenAsync(executeRequestWithToken);
                        }
                        return newResponsePromise(response);
                    }
                };
        return ssoTokenHolder.findToken(context, request)
                             .thenAsync(executeRequestWithToken)
                             .thenAsync(checkResponse);
    }

    @VisibleForTesting
    Request authenticationRequest(final Bindings bindings) {
        final Request request = new Request();
        request.setMethod("POST");
        request.setUri(openamUrl.resolve(BASE_ENDPOINT + realm + AUTHENTICATION_ENDPOINT));
        request.setEntity(json(object()).asMap());
        request.getHeaders().put("X-OpenAM-Username", username.eval(bindings));
        request.getHeaders().put("X-OpenAM-Password", password.eval(bindings));
        return request;
    }

    private class SsoTokenHolder {

        private final Handler ssoClientHandler;
        private final Logger logger;
        private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
        private Promise<String, NeverThrowsException> token;
        private boolean isTokenValid = false;

        SsoTokenHolder(final Handler ssoClientHandler, final Logger logger) {
            this.ssoClientHandler = ssoClientHandler;
            this.logger = logger;
        }

        Promise<String, NeverThrowsException> findToken(final Context context, final Request request) {
            rwl.readLock().lock();
            if (!isTokenValid) {
                // Must release read lock before acquiring write lock
                rwl.readLock().unlock();
                rwl.writeLock().lock();
                try {
                    // Re-check state because another thread might have
                    // acquired write lock and changed state before we did.
                    if (!isTokenValid) {
                        token = createSsoToken(context, request);
                        isTokenValid = true;
                    }
                    // Downgrade by acquiring read lock before releasing write lock
                    rwl.readLock().lock();
                } finally {
                    // Unlock write, still hold read
                    rwl.writeLock().unlock();
                }
            }

            try {
                return token;
            } finally {
                rwl.readLock().unlock();
            }
        }

        Promise<String, NeverThrowsException> updateToken(final Context context, final Request request) {
            rwl.writeLock().lock();
            try {
                isTokenValid = false;
            } finally {
                rwl.writeLock().unlock();
            }
            return findToken(context, request);
        }

        private Promise<String, NeverThrowsException> createSsoToken(final Context context, final Request request) {
            return ssoClientHandler.handle(context, authenticationRequest(bindings(context, request)))
                                   .then(extractSsoToken());
        }

        private Function<Response, String, NeverThrowsException> extractSsoToken() {
            return new Function<Response, String, NeverThrowsException>() {
                @Override
                public String apply(Response response) {
                    try {
                        @SuppressWarnings("unchecked")
                        final Map<String, String> result = (Map<String, String>) response.getEntity().getJson();
                        return result.get("tokenId");
                    } catch (IOException e) {
                        logger.warning("Couldn't parse as JSON the OpenAM authentication response");
                        logger.warning(e);
                    }
                    return null;
                }
            };
        }
    }
}
