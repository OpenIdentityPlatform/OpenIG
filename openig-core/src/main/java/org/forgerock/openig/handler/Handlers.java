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

package org.forgerock.openig.handler;

import static org.forgerock.http.protocol.Response.newResponsePromise;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Provides commonly used handler implementations.
 */
public final class Handlers {

    /**
     * Utility class.
     */
    private Handlers() { }

    /**
     * A default {@link Handler} implementation that returns an empty ({@literal 403 Forbidden}) {@link Response}.
     */
    public static final Handler FORBIDDEN = new Handler() {
        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            return newResponsePromise(new Response(Status.FORBIDDEN));
        }
    };

    /**
     * A default {@link Handler} implementation that returns an empty ({@literal 204 No Content}) {@link Response}.
     */
    public static final Handler NO_CONTENT = new Handler() {
        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            return newResponsePromise(new Response(Status.NO_CONTENT));
        }
    };
}
