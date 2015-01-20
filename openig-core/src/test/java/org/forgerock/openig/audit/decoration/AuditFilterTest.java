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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.audit.decoration;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

import java.io.IOException;

import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuditFilterTest extends AbstractAuditTest {

    @Mock
    private Filter delegate;

    @Mock
    private Handler handler;

    @Test
    public void shouldEmitAuditEventsWhenCompleted() throws Exception {
        AuditFilter audit = new AuditFilter(auditSystem, source, delegate, singleton("tag"));
        Exchange exchange = new Exchange();
        audit.filter(exchange, handler);

        verify(auditSystem, times(2)).onAuditEvent(captor.capture());
        assertThatEventIncludes(captor.getAllValues().get(0), exchange, "tag", "request");
        assertThatEventIncludes(captor.getAllValues().get(1), exchange, "tag", "response", "completed");
    }

    @DataProvider
    public static Object[][] supportedExceptions() {
        // @Checkstyle:off
        return new Object[][] {
                {new HandlerException("boom")},
                {new IOException("boom")}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "supportedExceptions")
    public void shouldEmitAuditEventsWhenFailed(Exception cause) throws Exception {
        doThrow(cause).when(delegate).filter(any(Exchange.class), eq(handler));
        AuditFilter audit = new AuditFilter(auditSystem, source, delegate, singleton("tag"));

        Exchange exchange = new Exchange();
        try {
            audit.filter(exchange, handler);
            failBecauseExceptionWasNotThrown(HandlerException.class);
        } catch (Exception e) {
            verify(auditSystem, times(2)).onAuditEvent(captor.capture());
            assertThatEventIncludes(captor.getAllValues().get(0), exchange, "tag", "request");
            assertThatEventIncludes(captor.getAllValues().get(1), exchange, "tag", "response", "exception");
        }
    }
}
