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
import static org.mockito.Matchers.eq;
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

public class TransactionIdForwardFilterTest {

    @Test
    public void shouldNotModifyRequestHeadersWhenNoTransactionIdContext() {
        TransactionIdForwardFilter filter = new TransactionIdForwardFilter();

        Handler handler = mock(Handler.class);
        Request request = new Request();

        filter.filter(new RootContext(), request, handler);

        verify(handler).handle(any(Context.class), eq(request));
        assertThat(request.getHeaders()).isEmpty();
    }

    @Test
    public void shouldAddRequestHeaderWhenTransactionIdContext() {
        TransactionIdForwardFilter filter = new TransactionIdForwardFilter();

        Handler handler = mock(Handler.class);
        Request request = new Request();
        TransactionIdContext context = new TransactionIdContext(new RootContext(), new TransactionId("txId"));

        filter.filter(context, request, handler);

        ArgumentCaptor<TransactionIdContext> contextCaptor = ArgumentCaptor.forClass(TransactionIdContext.class);
        verify(handler).handle(contextCaptor.capture(), eq(request));
        assertThat(request.getHeaders().getFirst("X-ForgeRock-TransactionId")).isEqualTo("txId/0");
        assertThat(contextCaptor.getValue()).isSameAs(context);
    }

}
