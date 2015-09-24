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

package org.forgerock.openig.openam;

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.FORBIDDEN;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.http.Responses.newInternalServerError;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.http.Exchange;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Provides an OpenAM SSO Token in the {@literal iPlanetDirectoryPro} header for downstream components.
 *
 * <p>The SSO Token is stored in the session to avoid DOS on OpenAM endpoints.
 *
 * <p>If the request failed, a unique attempt to refresh the SSO token is tried.
 */
public class SsoTokenFilter extends GenericHeapObject implements Filter {

    static final String SSO_TOKEN_KEY = "SSOToken";
    static final String BASE_ENDPOINT = "json";
    static final String AUTHENTICATION_ENDPOINT = "/authenticate";

    private final Handler ssoClientHandler;
    private final URI openamUrl;
    private final String realm;
    private final Expression<String> username;
    private final Expression<String> password;

    SsoTokenFilter(final Handler ssoClientHandler,
                   final URI openamUrl,
                   final String realm,
                   final Expression<String> username,
                   final Expression<String> password) {
        this.ssoClientHandler = checkNotNull(ssoClientHandler);
        this.openamUrl = checkNotNull(openamUrl);
        this.realm = startsWithSlash(realm);
        this.username = username;
        this.password = password;
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
                            request.getHeaders().put("iPlanetDirectoryPro", token);
                            return next.handle(context, request);
                        } else {
                            return newResponsePromise(newInternalServerError("Unable to retrieve SSO Token"));
                        }
                    }
                };

        final AsyncFunction<Response, Response, NeverThrowsException> checkResponse =
                new AsyncFunction<Response, Response, NeverThrowsException>() {

                    @Override
                    public Promise<Response, NeverThrowsException> apply(Response response) {
                        if (response.getStatus().equals(FORBIDDEN)) {
                            final SessionContext sessionContext = context.asContext(SessionContext.class);
                            sessionContext.getSession().remove(SSO_TOKEN_KEY);
                            return createSsoToken(context, request)
                                    .thenAsync(executeRequestWithToken);
                        }
                        return newResponsePromise(response);
                    }
                };

        return findSsoToken(context, request)
                .thenAsync(executeRequestWithToken)
                .thenAsync(checkResponse);
    }

    private Promise<String, NeverThrowsException> findSsoToken(final Context context, final Request request) {
        final SessionContext sessionContext = context.asContext(SessionContext.class);
        if (sessionContext.getSession().containsKey(SSO_TOKEN_KEY)) {
            return newResultPromise((String) sessionContext.getSession().get(SSO_TOKEN_KEY));
        } else {
            return createSsoToken(context, request);
        }
    }

    private Promise<String, NeverThrowsException> createSsoToken(final Context context, final Request request) {
        Exchange exchange = context.asContext(Exchange.class);
        return ssoClientHandler.handle(context, authenticationRequest(bindings(exchange, request)))
                               .then(extractSsoToken(context));
    }

    private Function<Response, String, NeverThrowsException> extractSsoToken(final Context context) {
        return new Function<Response, String, NeverThrowsException>() {
            @Override
            public String apply(Response response) {
                String token = null;
                try {
                    @SuppressWarnings("unchecked")
                    final Map<String, String> result = (Map<String, String>) response.getEntity().getJson();
                    token = result.get("tokenId");
                    context.asContext(SessionContext.class).getSession().put(SSO_TOKEN_KEY, token);
                } catch (IOException e) {
                    logger.warning("Couldn't parse as JSON the OpenAM authentication response");
                    logger.warning(e);
                }
                return token;
            }
        };
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
}
