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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.forgerock.audit.events.TransactionId;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.http.TransactionIdContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;


public class TransactionIdFilterTest {

    private static final String HEADER_TX_ID = "X-ForgeRock-TransactionId";

    @Test
    public void shouldCreateTransactionIdFromTheHeaderValue() {
        Request request = new Request();
        request.getHeaders().put(HEADER_TX_ID, "txId");

        TransactionId txId = new TransactionIdFilter().createTransactionId(request);

        assertThat(txId.getValue()).isEqualTo("txId");
    }

    @Test
    public void shouldCreateTransactionIdWhenTheHeaderValueIsEmpty() {
        Request request = new Request();
        request.getHeaders().put(HEADER_TX_ID, "");

        TransactionId txId = new TransactionIdFilter().createTransactionId(request);

        assertThat(txId.getValue()).isNotNull().isNotEmpty();
    }

    @Test
    public void shouldCreateTransactionIdWhenNoHeader() {
        Request request = new Request();

        TransactionId txId = new TransactionIdFilter().createTransactionId(request);

        assertThat(txId.getValue()).isNotNull().isNotEmpty();
    }

    @Test
    public void shouldCreateTransactionIdContext() {
        TransactionIdFilter filter = new TransactionIdFilter();
        final Handler handler = mock(Handler.class);
        final RootContext rootContext = new RootContext();
        Request request = new Request();
        request.getHeaders().put(HEADER_TX_ID, "txId");

        filter.filter(rootContext, request, handler);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(handler).handle(contextCaptor.capture(), any(Request.class));
        final Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getParent()).isSameAs(rootContext);
        assertThat(((TransactionIdContext) capturedContext).getTransactionId().getValue()).isEqualTo("txId");
    }

}
