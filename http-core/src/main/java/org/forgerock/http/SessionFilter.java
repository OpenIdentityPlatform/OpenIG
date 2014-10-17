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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.http;

import static org.forgerock.http.HttpApplication.LOGGER;

import org.forgerock.util.Reject;
import org.forgerock.util.promise.FailureHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.SuccessHandler;

import java.io.IOException;

class SessionFilter implements Filter {
    private SessionManager sessionManager;

    SessionFilter(SessionManager sessionManager) {
        Reject.ifNull(sessionManager, "sessionManager must not be null");
        this.sessionManager = sessionManager;
    }

    @Override
    public Promise<Response, ResponseException> filter(final Context context, Request request, Handler next) throws ResponseException {

        final Session oldSession = context.getSession();
        context.setSession(sessionManager.load(request));

        return next.handle(context, request)
                .then(new SuccessHandler<Response>() {
                    @Override
                    public void handleResult(Response response) {
                        saveSession(context.getSession(), response);
                    }
                }, new FailureHandler<ResponseException>() {
                    @Override
                    public void handleError(ResponseException error) {
                        saveSession(context.getSession(), error.getResponse());
                    }
                })
                .thenAlways(new Runnable() {
                    @Override
                    public void run() {
                        context.setSession(oldSession);
                    }
                });
    }

    private void saveSession(Session session, Response response) {
        try {
            sessionManager.save(session, response);
        } catch (IOException e) {
            LOGGER.error("Failed to save session", e);
        }
    }
}
