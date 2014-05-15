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
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.filter;

// Java Standard Edition

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;

// OpenIG Core
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.log.LogTimer;

/**
 * A chain of exchange zero or more filters and one handler. The chain is responsible for
 * dispatching the exchange to each filter in the chain, and finally the handler.
 * <p/>
 * When a chain dispatches an exchange to a filter, it creates a "subchain" (a subset of this
 * chain, which contains the remaining downstream filters and handler), and passes it as a
 * parameter to the filter. For this reason, a filter should make no assumptions or
 * correlations using the chain it is supplied with when invoked.
 * <p/>
 * A filter may elect to terminate dispatching of the exchange to the rest of the chain by not
 * calling {@code chain.handle(exchange)} and generate its own response or dispatch to a
 * completely different handler.
 *
 * @see Filter
 */
public class Chain extends GenericHandler {

    /** A list of filters, in the order they are to be dispatched by the chain. */
    public final List<Filter> filters = new ArrayList<Filter>();

    /** The handler dispatch the exchange to; terminus of the chain. */
    public Handler handler;

    @Override
    public void handle(Exchange exchange) throws HandlerException, IOException {
        LogTimer timer = logger.getTimer().start();
        new Handler() {
            private int cursor = 0;

            @Override
            public void handle(Exchange exchange) throws HandlerException, IOException {
                int saved = cursor; // save position to restore after the call
                try {
                    if (cursor < filters.size()) {
                        filters.get(cursor++).filter(exchange, this);
                    } else {
                        handler.handle(exchange);
                    }
                } finally {
                    cursor = saved;
                }
            }
        }.handle(exchange);
        timer.stop();
    }

    /** Creates and initializes a filter chain in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            Chain chain = new Chain();
            for (JsonValue filter : config.get("filters").required().expect(List.class)) {
                chain.filters.add(HeapUtil.getRequiredObject(heap, filter, Filter.class));
            }
            chain.handler = HeapUtil.getRequiredObject(heap, config.get("handler"), Handler.class);
            return chain;
        }
    }
}
