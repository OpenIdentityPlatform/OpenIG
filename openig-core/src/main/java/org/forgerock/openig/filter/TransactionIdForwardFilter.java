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
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import org.forgerock.audit.events.TransactionId;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.http.TransactionIdContext;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This filter aims to include the current transaction's id value as a header of the request.
 *
 * Its main usage will be like this :
 * <pre>
 * {@code
 * Handler handler = Handlers.chainOf(new ClientHandler(httpClient), new TransactionIdForwardFilter());
 * }
 * </pre>
 *
 */
public class TransactionIdForwardFilter extends GenericHeapObject implements Filter {

    /**
     * Creates a new TransactionIdForwardFilter.
     */
    TransactionIdForwardFilter() {
    }

    /**
     * Creates a new TransactionIdForwardFilter using the specified logger for any log messages.
     *
     * @param logger the logger to use for log messages.
     */
    public TransactionIdForwardFilter(Logger logger) {
        setLogger(logger);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        if (context.containsContext(TransactionIdContext.class)) {
            TransactionIdContext txContext = context.asContext(TransactionIdContext.class);
            final String subTxId = txContext.getTransactionId().createSubTransactionId().getValue();
            request.getHeaders().put(TransactionId.HTTP_HEADER, subTxId);
        } else {
            logger.debug("Expecting to find an instance of TransactionIdContext in the chain, but there was none.");
        }

        return next.handle(context, request);
    }

}
