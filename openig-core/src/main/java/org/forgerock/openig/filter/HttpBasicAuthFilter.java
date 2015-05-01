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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

// TODO: distinguish between basic and other schemes that use 401 (Digest, OAuth, ...)

package org.forgerock.openig.filter;

import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpContext;
import org.forgerock.http.Session;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Promise;

/**
 * Performs authentication through the HTTP Basic authentication scheme. For more information,
 * see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>.
 * <p>
 * If challenged for authentication via a {@code 401 Unauthorized} status code by the server,
 * this filter will retry the request with credentials attached. Therefore, the request entity
 * will be branched and stored for the duration of the exchange.
 * <p>
 * Once an HTTP authentication challenge (status code 401) is issued from the remote server,
 * all subsequent requests to that remote server that pass through the filter will include the
 * user credentials.
 * <p>
 * Credentials are cached in the session to allow subsequent requests to automatically include
 * authentication credentials. If authentication fails (including the case of no credentials
 * yielded from the {@code username} or {@code password} expressions, then the exchange is diverted
 * to the authentication failure handler.
 */
public class HttpBasicAuthFilter extends GenericHeapObject implements org.forgerock.http.Filter {

    /** Headers that are suppressed from incoming request. */
    private static final CaseInsensitiveSet SUPPRESS_REQUEST_HEADERS =
            new CaseInsensitiveSet(Arrays.asList("Authorization"));

    /** Headers that are suppressed for outgoing response. */
    private static final CaseInsensitiveSet SUPPRESS_RESPONSE_HEADERS =
            new CaseInsensitiveSet(Arrays.asList("WWW-Authenticate"));

    /** Expression that yields the username to supply during authentication. */
    private final Expression<String> username;

    /** Expression that yields the password to supply during authentication. */
    private final Expression<String> password;

    /** Handler dispatch to if authentication fails. */
    private final Handler failureHandler;

    /** Decide if we cache the password header result. */
    private boolean cacheHeader = true;

    /**
     * Builds a {@code HttpBasicAuthFilter} with required expressions and error handler.
     * @param username the expression that yields the username to supply during authentication.
     * @param password the expression that yields the password to supply during authentication.
     * @param failureHandler the Handler to dispatch to if authentication fails.
     */
    public HttpBasicAuthFilter(final Expression<String> username,
            final Expression<String> password,
            final Handler failureHandler) {
        this.username = username;
        this.password = password;
        this.failureHandler = failureHandler;
    }

    /**
     * Decide if we cache the password header result (defaults to {@literal true}).
     * @param cacheHeader cache (or not) the {@literal Authorization} header
     */
    public void setCacheHeader(final boolean cacheHeader) {
        this.cacheHeader = cacheHeader;
    }

    /**
     * Resolves a session attribute name for the remote server specified in the specified
     * request.
     *
     * @param request the request of the attribute to resolve.
     * @return the session attribute name, fully qualified the request remote server.
     */
    private String attributeName(Request request) {
        return getClass().getName() + ':' + request.getUri().getScheme() + ':'
                + request.getUri().getHost() + ':' + request.getUri().getPort() + ':' + "userpass";
    }

    @Override
    public Promise<Response, ResponseException> filter(final Context context,
                                                       final Request request,
                                                       final Handler next) {

        Exchange exchange = context.asContext(Exchange.class);
        Session session = context.asContext(HttpContext.class).getSession();

        // Remove existing headers from incoming message
        for (String header : SUPPRESS_REQUEST_HEADERS) {
            request.getHeaders().remove(header);
        }

        String userpass = null;

        // loop to retry for initially retrieved (or refreshed) credentials
        try {
            for (int n = 0; n < 2; n++) {
                // put a branch of the trunk in the entity to allow retries
                request.getEntity().push();
                Response response;
                try {
                    // because credentials are sent in every request, this class caches them in the session
                    if (cacheHeader) {
                        userpass = (String) session.get(attributeName(request));
                    }
                    if (userpass != null) {
                        request.getHeaders().add("Authorization", "Basic " + userpass);
                    }
                    response = next.handle(context, request).getOrThrow();
                } finally {
                    request.getEntity().pop();
                }
                // successful exchange from this filter's standpoint
                if (!Status.UNAUTHORIZED.equals(response.getStatus())) {
                    // Remove headers from outgoing message
                    for (String header : SUPPRESS_RESPONSE_HEADERS) {
                        response.getHeaders().remove(header);
                    }
                    return newResultPromise(response);
                }
                // close the incoming response because it's about to be dereferenced
                closeSilently(response);

                // credentials might be stale, so fetch them
                String user = username.eval(exchange);
                String pass = password.eval(exchange);
                // no credentials is equivalent to invalid credentials
                if (user == null || pass == null) {
                    break;
                }
                // ensure conformance with specification
                if (user.indexOf(':') >= 0) {
                    return newExceptionPromise(
                            new ResponseException("username must not contain a colon ':' character"));
                }
                if (cacheHeader) {
                    // set in session for fetch in next iteration of this loop
                    session.put(attributeName(request),
                                         Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset())));
                } else {
                    userpass = Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset()));
                }
            }
        } catch (Exception e) {
            return newExceptionPromise(
                    new ResponseException("Can't authenticate user with Basic Http Authorization", e));
        }


        // credentials were missing or invalid; let failure handler deal with it
        return failureHandler.handle(context, request);
    }

    /** Creates and initializes an HTTP basic authentication filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            Handler failureHandler =
                    heap.resolve(config.get("failureHandler"), Handler.class);

            Expression<String> usernameExpr = asExpression(config.get("username").required(), String.class);
            Expression<String> passwordExpr = asExpression(config.get("password").required(), String.class);
            HttpBasicAuthFilter filter = new HttpBasicAuthFilter(usernameExpr, passwordExpr, failureHandler);

            filter.cacheHeader = config.get("cacheHeader").defaultTo(filter.cacheHeader).asBoolean();

            logger.debug("HttpBasicAuthFilter: cacheHeader set to " + filter.cacheHeader);

            return filter;
        }
    }
}
