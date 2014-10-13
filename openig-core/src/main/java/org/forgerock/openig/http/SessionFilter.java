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

package org.forgerock.openig.http;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.forgerock.http.ResponseException;
import org.forgerock.http.Session;
import org.forgerock.http.SessionFactory;
import org.forgerock.util.promise.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

import java.io.IOException;

public class SessionFilter implements Filter {

    private SessionFactory sessionFactory;

    public SessionFilter(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Promise<Response, ResponseException> filter(final Context context, Request request, Handler next) throws ResponseException {

        if (sessionFactory == null) {
            return next.handle(context, request);
        }

        context.setSession(sessionFactory.load(request));

        return next.handle(context, request)
                .thenAsync(new AsyncFunction<Response, Response, ResponseException>() {
                    @Override
                    public Promise<Response, ResponseException> apply(Response response) throws ResponseException {
                        return saveSession(context.getSession(), response);
                    }
                }, new AsyncFunction<ResponseException, Response, ResponseException>() {
                    @Override
                    public Promise<Response, ResponseException> apply(ResponseException error) throws ResponseException {
                        return saveSession(context.getSession(), error.getResponse());
                    }
                });
    }

    private Promise<Response, ResponseException> saveSession(Session session, Response response) {
        try {
            session.save(response);
            return Promises.newSuccessfulPromise(response);
        } catch (IOException e) {
            HttpHandler.LOG.error("Failed to save session", e);
            return Promises.newSuccessfulPromise(response);
        }
    }
}
