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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.forgerock.util.promise.Promise;

/**
 * Utility methods for creating common types of handler and filters.
 */
public class Http {
    /*
     * FIXME: handlers and filters should always return responses regardless of
     * the status code. Exceptions should be reserved for internal failures.
     */

    public static Filter newSessionFilter(SessionManager sessionManager) {
        return new SessionFilter(sessionManager);
    }

    public static Handler chainOf(final Handler handler, final Filter... filters) {
        return chainOf(handler, Arrays.asList(filters));
    }

    public static Handler chainOf(final Handler handler, final List<Filter> filters) {
        return new Chain(handler, filters, 0);
    }

    public static Filter chainOf(final Filter... filters) {
        return chainOf(Arrays.asList(filters));
    }

    public static Filter chainOf(final Collection<Filter> filters) {
        // TODO: return a subsequence of filters.
        return null;
    }

    private static final class Chain implements Handler {
        private final Handler handler;
        private final List<Filter> filters;
        private final int position;

        private Chain(Handler handler, List<Filter> filters, int position) {
            this.handler = handler;
            this.filters = filters;
            this.position = position;
        }

        @Override
        public Promise<Response, ResponseException> handle(Context context, Request request)
                throws ResponseException {
            if (position < filters.size()) {
                return filters.get(position).filter(context, request, next());
            } else {
                return handler.handle(context, request);
            }
        }

        private Handler next() {
            return new Chain(handler, filters, position + 1);
        }

        @Override
        public String toString() {
            return filters.toString() + " -> " + handler.toString();
        }
    }

    private Http() {
        // Prevent instantiation.
    }
}
