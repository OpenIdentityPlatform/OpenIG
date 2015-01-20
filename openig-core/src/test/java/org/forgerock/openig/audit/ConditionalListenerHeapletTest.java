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

package org.forgerock.openig.audit;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.audit.AuditSystem.*;
import static org.mockito.Mockito.*;

import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.Name;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConditionalListenerHeapletTest {

    @Mock
    private AuditEventListener delegate;

    @Mock
    private AuditSystem system;

    @Mock
    private Heap heap;

    @Captor
    private ArgumentCaptor<AuditEventListener> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(heap.get(AUDIT_SYSTEM_HEAP_KEY, AuditSystem.class)).thenReturn(system);
    }

    private class TestingHeaplet extends ConditionalAuditEventListener.ConditionalListenerHeaplet {
        @Override
        protected AuditEventListener createListener() {
            return delegate;
        }
    }

    @Test
    public void shouldCreateDelegateListenerThenRegisterConditionalListenerAndUnRegisterDuringDestroy()
            throws Exception {
        TestingHeaplet heaplet = new TestingHeaplet();

        // Verify registration
        Object o = heaplet.create(Name.of("source"), json(object()), heap);
        assertThat(o).isSameAs(delegate);
        verify(system).registerListener(captor.capture());

        // Verify un-registration
        heaplet.destroy();
        verify(system).unregisterListener(captor.capture());

        assertThat(captor.getAllValues().get(0)).isSameAs(captor.getAllValues().get(1));
        assertThat(captor.getAllValues().get(0)).isInstanceOf(ConditionalAuditEventListener.class);
    }
}
