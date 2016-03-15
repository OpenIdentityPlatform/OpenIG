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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

// TODO: distinguish between basic and other schemes that use 401 (Digest, OAuth, ...)

package org.forgerock.openig.filter;

import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Entity;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.http.util.CaseInsensitiveSet;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Performs authentication through the HTTP Basic authentication scheme. For more information,
 * see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>.
 * <p>
 * If challenged for authentication via a {@code 401 Unauthorized} status code by the server,
 * this filter will retry the request with credentials attached. Therefore, the request entity
 * will be branched and stored for the duration of the processing.
 * <p>
 * Once an HTTP authentication challenge (status code 401) is issued from the remote server,
 * all subsequent requests to that remote server that pass through the filter will include the
 * user credentials.
 * <p>
 * Credentials are cached in the session to allow subsequent requests to automatically include
 * authentication credentials. If authentication fails (including the case of no credentials
 * yielded from the {@code username} or {@code password} expressions, then the processing is diverted
 * to the authentication failure handler.
 */
public class HttpBasicAuthFilter extends GenericHeapObject implements Filter {

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
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        final Session session = context.asContext(SessionContext.class).getSession();

        // Remove existing headers from incoming message
        for (String header : SUPPRESS_REQUEST_HEADERS) {
            request.getHeaders().remove(header);
        }

        // Ensure we always work on an entity's branch while executing the handler
        final Handler wrappedNext = Handlers.chainOf(next, new PushPopFilter(logger));

        // Here is the scenario :
        // 1 - try to execute the handler with the cached credentials if any.
        // 2.a - if the response is not UNAUTHORIZED : return it
        // 2.b - if the response is UNAUTHORIZED : re-execute the handler with the freshly computed credentials
        // 2.b.1 - if the response is not UNAUTHORIZED : return it
        // 2.b.2 - if the response is still UNAUTHORIZED : re-execute the handler with the freshly computed credentials

        final String cachedUserpass = (String) session.get(attributeName(request));

        // Execute with cached credentials or not ?
        // cachedUserpass can be null if that's the first time
        boolean executeWithCachedCredentials = cacheHeader && cachedUserpass != null;
        if (executeWithCachedCredentials) {
            setAuthorizationHeader(request.getHeaders(), cachedUserpass);
            // Let's try first to execute the request with the cached credentials.
            // if that's not successful, then try again with freshly computed credentials.
            return wrappedNext.handle(context, request)
                              .thenAsync(ifUnauthorized(executeWithCredentials(context, request, wrappedNext)));
        } else {
            return executeWithCredentialsFilter().filter(context, request, wrappedNext);
        }
    }

    private AsyncFunction<Void, Response, NeverThrowsException> executeWithCredentials(final Context context,
                                                                                       final Request request,
                                                                                       final Handler next) {
        return new AsyncFunction<Void, Response, NeverThrowsException>() {
            @Override
            public Promise<? extends Response, ? extends NeverThrowsException> apply(Void value) {
                return executeWithCredentialsFilter().filter(context, request, next);
            }
        };
    }

    private Filter executeWithCredentialsFilter() {
        return new Filter() {
            @Override
            public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
                // Retry with the evaluated expressions as they may have not been cached or obsolete.
                Bindings bindings = bindings(context, request);
                String user = username.eval(bindings);
                String pass = password.eval(bindings);
                // no credentials is equivalent to invalid credentials
                if (user == null || pass == null) {
                    logger.debug("Invalid credentials evaluated from the expressions");
                    // credentials were missing or invalid; let failure handler deal with it
                    return failureHandler.handle(context, request);
                }
                // ensure conformance with specification
                if (user.indexOf(':') >= 0) {
                    logger.error("username must not contain a colon ':' character");
                    return newResultPromise(newInternalServerError());
                }

                final String userpass = Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset()));
                // because credentials are sent in every request, this class caches them in the session
                if (cacheHeader) {
                    SessionContext sessionContext = context.asContext(SessionContext.class);
                    sessionContext.getSession().put(attributeName(request), userpass);
                }

                setAuthorizationHeader(request.getHeaders(), userpass);

                return next.handle(context, request)
                           .thenAsync(ifUnauthorized(fail(context, request)));
            }

            private AsyncFunction<Void, Response, NeverThrowsException> fail(final Context context,
                                                                             final Request request) {
                return new AsyncFunction<Void, Response, NeverThrowsException>() {
                    @Override
                    public Promise<? extends Response, ? extends NeverThrowsException> apply(Void value) {
                        // The 2nd try was not successful (invalid credentials), handle the failure.
                        return failureHandler.handle(context, request);
                    }
                };
            }
        };
    }

    private void setAuthorizationHeader(Headers headers, String userpass) {
        if (userpass != null) {
            headers.put("Authorization", "Basic " + userpass);
        }
    }

    /**
     * Filter that ensures the next handler will work on a branch of the entity's content.
     */
    private static class PushPopFilter implements Filter {

        private Logger logger;

        public PushPopFilter(Logger logger) {
            this.logger = logger;
        }

        @Override
        public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler handler) {
            Entity entity = request.getEntity();

            try {
                entity.push();
                return handler.handle(context, request);
            } catch (IOException e) {
                logger.error("Can't authenticate user with Basic Http Authorization");
                logger.error(e);
                return newResultPromise(new Response(Status.FORBIDDEN));
            } finally {
                entity.pop();
            }
        }
    }

    private IfUnauthorizedFunction ifUnauthorized(AsyncFunction<Void, Response, NeverThrowsException> function) {
        return new IfUnauthorizedFunction(function);
    }

    /**
     * This function checks the response's status : if it is not UNAUTHORIZED then it returns the response, but if is
     * UNAUTHORIZED then it executes the given function.
     */
    private static class IfUnauthorizedFunction implements AsyncFunction<Response, Response, NeverThrowsException> {

        private final AsyncFunction<Void, Response, NeverThrowsException> onUnauthorized;

        IfUnauthorizedFunction(AsyncFunction<Void, Response, NeverThrowsException> onUnauthorized) {
            this.onUnauthorized = onUnauthorized;
        }

        @Override
        public Promise<? extends Response, ? extends NeverThrowsException> apply(Response response) {
            // successful response from this filter's standpoint
            if (!Status.UNAUTHORIZED.equals(response.getStatus())) {
                // Remove headers from outgoing message
                for (String header : SUPPRESS_RESPONSE_HEADERS) {
                    response.getHeaders().remove(header);
                }
                return newResponsePromise(response);
            }
            // close the incoming response because it's about to be dereferenced
            closeSilently(response);

            return onUnauthorized.apply(null);
        }
    }

    /** Creates and initializes an HTTP basic authentication filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            Handler failureHandler = heap.resolve(config.get("failureHandler"), Handler.class);

            Expression<String> usernameExpr = asExpression(config.get("username").required(), String.class);
            Expression<String> passwordExpr = asExpression(config.get("password").required(), String.class);
            HttpBasicAuthFilter filter = new HttpBasicAuthFilter(usernameExpr, passwordExpr, failureHandler);

            filter.cacheHeader = config.get("cacheHeader").defaultTo(filter.cacheHeader).asBoolean();

            logger.debug("HttpBasicAuthFilter: cacheHeader set to " + filter.cacheHeader);

            return filter;
        }
    }
}
