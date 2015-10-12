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

import java.util.List;

import org.forgerock.audit.events.TransactionId;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Header;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.http.TransactionIdContext;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This filter is responsible to create the {@link TransactionIdContext} in the context's chain. If the incoming request
 * contains the header "X-ForgeRock-TransactionId" then it uses that value as the transaction id otherwise a new one is
 * generated.
 */
public class TransactionIdFilter extends GenericHeapObject implements Filter {

    /**
     * Creates a new TransactionIdFilter.
     */
    TransactionIdFilter() {
        super();
    }

    /**
     * Creates a new TransactionIdFilter using the specified logger for any log messages.
     *
     * @param logger the logger to use for log messages.
     */
    public TransactionIdFilter(Logger logger) {
        setLogger(logger);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        if (context.containsContext(TransactionIdContext.class)) {
            logger.debug("A TransactionIdContext already exists in the context's chain.");
        }

        final Context newContext = new TransactionIdContext(context, createTransactionId(request));
        return next.handle(newContext, request);
    }

    @VisibleForTesting
    TransactionId createTransactionId(Request request) {
        final Headers headers = request.getHeaders();
        if (headers.containsKey(TransactionId.HTTP_HEADER)) {
            final Header txHeader = headers.get(TransactionId.HTTP_HEADER);

            List<String> values = txHeader.getValues();
            if (values.isEmpty()) {
                logger.debug("The TransactionId header is present but has no value.");
                return new TransactionId();
            }
            if (values.size() > 1) {
                logger.debug("The TransactionId header cannot be multi-valued, using the first value.");
            }
            final String value = values.get(0);
            if (value.isEmpty()) {
                logger.debug("The TransactionId header is present but has no value.");
                return new TransactionId();
            }
            return new TransactionId(value);
        } else {
            return new TransactionId();
        }
    }
}
