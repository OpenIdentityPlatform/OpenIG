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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.openig.util.JsonValues.ofRequiredHeapObject;

import java.util.List;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * A chain of exchange zero or more filters and one handler. The chain is responsible for
 * dispatching the exchange to each filter in the chain, and finally the handler.
 * <p>
 * When a chain dispatches an exchange to a filter, it creates a "subchain" (a subset of this
 * chain, which contains the remaining downstream filters and handler), and passes it as a
 * parameter to the filter. For this reason, a filter should make no assumptions or
 * correlations using the chain it is supplied with when invoked.
 * <p>
 * A filter may elect to terminate dispatching of the exchange to the rest of the chain by not
 * calling {@code chain.handle(exchange)} and generate its own response or dispatch to a
 * completely different handler.
 *
 * @see Filter
 */
public class Chain extends GenericHeapObject implements Handler {

    /** The CHF Chain implementation. */
    private final Handler delegate;

    /**
     * Builds a chain of filters that will finally dispatch to the given handler.
     * List of Filters is empty by default.
     * @param handler terminus of the chain
     * @param filters list of {@link Filter}s
     */
    public Chain(final Handler handler, final List<Filter> filters) {
        delegate = Handlers.chainOf(handler, filters);
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        return delegate.handle(context, request);
    }

    /** Creates and initializes a filter chain in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            Handler terminus = heap.resolve(config.get("handler"),
                                            Handler.class);
            JsonValue list = config.get("filters")
                                   .required()
                                   .expect(List.class);
            List<Filter> filters = list.asList(ofRequiredHeapObject(heap, Filter.class));
            return new Chain(terminus, filters);
        }
    }
}
