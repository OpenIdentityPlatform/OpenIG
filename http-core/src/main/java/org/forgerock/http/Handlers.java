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

import org.forgerock.util.promise.Promise;

import java.util.Arrays;
import java.util.Collection;

/**
 * Handler utility methods.
 */
public class Handlers {

    public static Handler chain(final Handler handler, final Filter... filters) {
        return chain(handler, Arrays.asList(filters));
    }

    public static Handler chain(final Handler handler,
            final Collection<Filter> filters) {
        return new FilterChain(handler, filters);
    }

    private Handlers() {
        // Prevent instantiation.
    }

    private static final class FilterChain implements Handler {

        private final class Cursor implements Handler {
            private final int pos;
            private final Filter[] snapshot;

            private Cursor() {
                this(filters.toArray(new Filter[0]), 0);
            }

            private Cursor(final Filter[] snapshot, final int pos) {
                this.snapshot = snapshot;
                this.pos = pos;
            }

            @Override
            public Promise<Response, ResponseException> handle(Context context, Request request) throws ResponseException {
                if (hasNext()) {
                    return get().filter(context, request, next());
                } else {
                    return target.handle(context, request);
                }
            }

            private Filter get() {
                return snapshot[pos];
            }

            private boolean hasNext() {
                return pos < snapshot.length;
            }

            private Cursor next() {
                return new Cursor(snapshot, pos + 1);
            }
        }

        private final Handler target;
        private final Collection<Filter> filters;

        private FilterChain(Handler target, Collection<Filter> filters) {
            this.target = target;
            this.filters = filters;
        }

        @Override
        public Promise<Response, ResponseException> handle(Context context, Request request) throws ResponseException {
            return new Cursor().handle(context, request);
        }
    }
}
