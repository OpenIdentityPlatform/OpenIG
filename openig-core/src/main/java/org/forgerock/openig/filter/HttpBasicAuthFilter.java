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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2009 Sun Microsystems Inc. All rights reserved.
 * Portions Copyrighted 2010–2011 ApexIdentity Inc.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

// TODO: distinguish between basic and other schemes that use 401 (Digest, OAuth, ...)

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

// OpenIG Core
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.BranchingInputStream;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.JsonValueUtil;
import org.forgerock.util.encode.Base64;

/**
 * Performs authentication through the HTTP Basic authentication scheme. For more information,
 * see <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a>.
 * <p/>
 * If challenged for authentication via a {@code 401 Unauthorized} status code by the server,
 * this filter will retry the request with credentials attached. Therefore, the request entity
 * will be branched and stored for the duration of the exchange.
 * <p/>
 * Once an HTTP authentication challenge (status code 401) is issued from the remote server,
 * all subsequent requests to that remote server that pass through the filter will include the
 * user credentials.
 * <p/>
 * If authentication fails (including the case of no credentials yielded from the
 * {@code username} or {@code password} expressions, then the exchange is diverted to the
 * authentication failure handler.
 */
public class HttpBasicAuthFilter extends GenericFilter {

    /** Headers that are suppressed from incoming request. */
    private static final CaseInsensitiveSet SUPPRESS_REQUEST_HEADERS =
            new CaseInsensitiveSet(Arrays.asList("Authorization"));

    /** Headers that are suppressed for outgoing response. */
    private static final CaseInsensitiveSet SUPPRESS_RESPONSE_HEADERS =
            new CaseInsensitiveSet(Arrays.asList("WWW-Authenticate"));

    /** Expression that yields the username to supply during authentication. */
    public Expression username;

    /** Expression that yields the password to supply during authentication. */
    public Expression password;

    /** Handler dispatch to if authentication fails. */
    public Handler failureHandler;

    /** Decide if we cache the password header result. */
    public boolean cacheHeader = true;

    /**
     * Resolves a session attribute name for the remote server specified in the specified
     * request.
     *
     * @param request the request of the attribute to resolve.
     * @return the session attribute name, fully qualified the request remote server.
     */
    private String attributeName(Request request) {
        return this.getClass().getName() + ':' + request.uri.getScheme() + ':'
                + request.uri.getHost() + ':' + request.uri.getPort() + ':' + "userpass";
    }

    /**
     * Handles the message exchange by authenticating via HTTP basic scheme. Credentials are
     * cached in the session to allow subsequent requests to automatically include
     * authentication credentials.
     */
    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        exchange.request.headers.remove(SUPPRESS_REQUEST_HEADERS);
        BranchingInputStream trunk = exchange.request.entity;
        String userpass = null;

        // loop to retry for initially retrieved (or refreshed) credentials
        for (int n = 0; n < 2; n++) {
            // put a branch of the trunk in the entity to allow retries
            if (trunk != null) {
                exchange.request.entity = trunk.branch();
            }
            // because credentials are sent in every request, this class caches them in the session
            if (cacheHeader) {
                userpass = (String) exchange.session.get(attributeName(exchange.request));
            }
            if (userpass != null) {
                exchange.request.headers.add("Authorization", "Basic " + userpass);
            }
            next.handle(exchange);
            // successful exchange from this filter's standpoint
            if (exchange.response.status != 401) {
                exchange.response.headers.remove(SUPPRESS_RESPONSE_HEADERS);
                timer.stop();
                return;
            }
            // credentials might be stale, so fetch them
            String user = username.eval(exchange, String.class);
            String pass = password.eval(exchange, String.class);
            // no credentials is equivalent to invalid credentials
            if (user == null || pass == null) {
                break;
            }
            // ensure conformance with specification
            if (user.indexOf(':') > 0) {
                throw new HandlerException("username must not contain a colon ':' character");
            }
            if (cacheHeader) {
                // set in session for fetch in next iteration of this loop
                exchange.session.put(attributeName(exchange.request),
                        Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset())));
            } else {
                userpass = Base64.encode((user + ":" + pass).getBytes(Charset.defaultCharset()));
            }
        }
        // close the incoming response because it's about to be dereferenced
        if (exchange.response.entity != null) {
            exchange.response.entity.close(); // important!
        }
        // credentials were missing or invalid; let failure handler deal with it
        exchange.response = new Response();
        failureHandler.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes an HTTP basic authentication filter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            HttpBasicAuthFilter filter = new HttpBasicAuthFilter();
            filter.username = JsonValueUtil.asExpression(config.get("username").required());
            filter.password = JsonValueUtil.asExpression(config.get("password").required());
            filter.failureHandler = HeapUtil.getObject(
                    heap,
                    config.get("failureHandler").required(),
                    Handler.class); // required
            filter.cacheHeader = config.get("cacheHeader").defaultTo(filter.cacheHeader).asBoolean(); // optional

            if (logger != null && logger.isLoggable(LogLevel.DEBUG)) {
                logger.debug("HttpBasicAuthFilter: cacheHeader set to " + filter.cacheHeader);
            }

            return filter;
        }
    }
}
