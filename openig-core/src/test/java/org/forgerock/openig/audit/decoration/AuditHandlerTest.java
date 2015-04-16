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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.audit.decoration;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuditHandlerTest extends AbstractAuditTest {

    @Mock
    private Handler delegate;

    @Test
    public void shouldEmitAuditEventsWhenCompleted() throws Exception {
        AuditHandler audit = new AuditHandler(auditSystem, source, delegate, singleton("tag"));
        Exchange exchange = new Exchange();

        when(delegate.handle(exchange, null))
                .thenReturn(Promises.<Response, ResponseException>newSuccessfulPromise(new Response()));

        audit.handle(exchange, null).get();

        verify(auditSystem, times(2)).onAuditEvent(captor.capture());

        assertThatEventIncludes(captor.getAllValues().get(0),
                                exchange,
                                "tag", "request");

        assertThatEventIncludes(captor.getAllValues().get(1),
                                exchange,
                                "tag", "response", "completed");
    }

    @Test
    public void shouldEmitAuditEventsWhenFailed() throws Exception {
        when(delegate.handle(any(Exchange.class), any(Request.class)))
                .thenReturn(Promises.<Response, ResponseException>newFailedPromise(new ResponseException(500)));

        AuditHandler audit = new AuditHandler(auditSystem, source, delegate, singleton("tag"));

        Exchange exchange = new Exchange();
        try {
            audit.handle(exchange, null).getOrThrow();
            failBecauseExceptionWasNotThrown(ResponseException.class);
        } catch (ResponseException e) {
            verify(auditSystem, times(2)).onAuditEvent(captor.capture());

            assertThatEventIncludes(captor.getAllValues().get(0),
                                    exchange,
                                    "tag", "request");

            assertThatEventIncludes(captor.getAllValues().get(1),
                                    exchange,
                                    "tag", "response", "exception");
        }
    }
}
