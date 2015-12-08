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

import static java.util.Collections.singleton;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.testng.annotations.Test;

@Deprecated
@SuppressWarnings("javadoc")
public class AuditHandlerTest extends AbstractAuditTest {

    @Mock
    private Handler delegate;

    @Test(dataProvider = "nullOrEmpty")
    public void shouldEmitAuditEventsWithAdditionalTagsContainingNullOrEmpty(final Set<String> additionalTags)
            throws Exception {
        AuditHandler audit = new AuditHandler(auditSystem, source, delegate, additionalTags);
        Context context = new RootContext();

        when(delegate.handle(context, null))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));

        audit.handle(context, null).get();
        verify(auditSystem, times(2)).onAuditEvent(captor.capture());

        assertThatEventIncludes(captor.getAllValues().get(0),
                                context,
                                "request");

        assertThatEventIncludes(captor.getAllValues().get(1),
                                context,
                                "response", "completed");
    }

    @Test
    public void shouldEmitAuditEventsWhenCompleted() throws Exception {
        AuditHandler audit = new AuditHandler(auditSystem, source, delegate, singleton("tag"));
        Context context = new RootContext();

        when(delegate.handle(context, null))
                .thenReturn(Promises.<Response, NeverThrowsException>newResultPromise(new Response()));

        audit.handle(context, null).get();

        verify(auditSystem, times(2)).onAuditEvent(captor.capture());

        assertThatEventIncludes(captor.getAllValues().get(0),
                                context,
                                "tag", "request");

        assertThatEventIncludes(captor.getAllValues().get(1),
                                context,
                                "tag", "response", "completed");
    }

}
