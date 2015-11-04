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
package org.forgerock.openig.http;

import org.forgerock.audit.events.TransactionId;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/**
 * This context aims to hold the {@link TransactionId} and is responsible to create sub-transactions ids when
 * the workflow forks.
 */
public class TransactionIdContext extends AbstractContext {

    private final TransactionId transactionId;

    /**
     * Constructs a new TransactionIdContext.
     *
     * @param parent The parent context
     * @param transactionId The transaction id to use in this context
     */
    public TransactionIdContext(Context parent, TransactionId transactionId) {
        super(transactionId.getValue(), "transactionId", parent);
        this.transactionId = transactionId;
    }

    /**
     * Returns the transaction id.
     * @return the transaction id
     */
    public TransactionId getTransactionId() {
        return transactionId;
    }

}
