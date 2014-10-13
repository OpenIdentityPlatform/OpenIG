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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.http;

import org.forgerock.util.functional.Lists;
import org.forgerock.util.promise.Promise;

import java.util.Arrays;
import java.util.List;

/**
 * Handler utility methods.
 */
public class Handlers {

    public static Handler chain(final Handler handler, final Filter... filters) {
        return chain(handler, Arrays.asList(filters));
    }

    public static Handler chain(final Handler handler,
            final List<Filter> filters) {
        return new Cursor(handler, Lists.asList(filters));
    }

    private static final class Cursor implements Handler {

        private final Handler handler;
        private final Lists.List<Filter> filters;

        private Cursor(Handler handler, Lists.List<Filter> filters) {
            this.handler = handler;
            this.filters = filters;
        }

        @Override
        public Promise<Response, ResponseException> handle(Context context, Request request) throws ResponseException {
            if (filters.tail().isEmpty()) {
                return filters.head().filter(context, request, handler);
            } else {
                return filters.head().filter(context, request, next());
            }
        }

        private Handler next() {
            return new Cursor(handler, filters.tail());
        }
    }

    private Handlers() {
        // Prevent instantiation.
    }
}
