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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.services.context.Context;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class GatewayDependencyProviderTest {

    @Test
    public void shouldUseProvidedClientHandlerWhenLookingForClient() throws Exception {
        Handler handler = mock(Handler.class);
        HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        heap.put("ElasticsearchClientHandler", handler);

        AuditServiceObjectHeaplet.GatewayDependencyProvider provider =
                new AuditServiceObjectHeaplet.GatewayDependencyProvider(heap);

        Client client = provider.getDependency(Client.class);

        Request request = new Request();
        client.send(request);

        verify(handler).handle(any(Context.class), eq(request));
    }

    @Test(expectedExceptions = ClassNotFoundException.class)
    public void shouldThrowAnExceptionWhenNoHandlerDefined() throws Exception {
        HeapImpl heap = HeapUtilsTest.buildDefaultHeap();
        AuditServiceObjectHeaplet.GatewayDependencyProvider provider =
                new AuditServiceObjectHeaplet.GatewayDependencyProvider(heap);

        provider.getDependency(Client.class);
    }
}
