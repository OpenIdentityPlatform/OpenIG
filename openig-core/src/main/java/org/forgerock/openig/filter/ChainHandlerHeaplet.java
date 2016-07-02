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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.List;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.handler.Handlers;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * A chain of zero or more filters and one handler. The chain is responsible for
 * dispatching the request to each filter in the chain, and finally the handler.
 * <p>
 * When a chain dispatches a request to a filter, it creates a "subchain" (a subset of this
 * chain, which contains the remaining downstream filters and handler), and passes it as a
 * parameter to the filter. For this reason, a filter should make no assumptions or
 * correlations using the chain it is supplied with when invoked.
 * <p>
 * A filter may elect to terminate dispatching of the request to the rest of the chain by not
 * calling {@code chain.handle(Context, Request)} and generate its own response or dispatch to a
 * completely different handler.
 *
 * @see Filter
 */
public class ChainHandlerHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        Handler terminus = config.get("handler").as(requiredHeapObject(heap, Handler.class));
        List<Filter> filters = config.get("filters")
                                     .required()
                                     .expect(List.class)
                                     .as(listOf(requiredHeapObject(heap, Filter.class)));
        return Handlers.chainOf(terminus, filters);
    }
}
