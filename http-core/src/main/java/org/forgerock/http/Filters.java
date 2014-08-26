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

import static org.forgerock.http.Handlers.asHandler;
import static org.forgerock.http.Handlers.handleResponse;

import java.util.Arrays;
import java.util.Collection;

/**
 * Filter utility methods.
 */
public class Filters {

    public static AsyncFilter asAsyncFilter(final Filter2 filter) {
        return new AsyncFilter() {

            @Override
            public void filter(final Context context, final Request request,
                    final ResponseHandler callback, final AsyncHandler next)
                    throws ResponseException {
                handleResponse(callback, filter.filter(context, request, asHandler(next)));
            }

        };
    }

    public static AsyncFilter compose(final AsyncFilter... filters) {
        return compose(Arrays.asList(filters));
    }

    public static AsyncFilter compose(final Collection<AsyncFilter> filters) {
        // TODO: return a subsequence of filters.
        return null;
    }

    private Filters() {
        // Prevent instantiation.
    }
}
