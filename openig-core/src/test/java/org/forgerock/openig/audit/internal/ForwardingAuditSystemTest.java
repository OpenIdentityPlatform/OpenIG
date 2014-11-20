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

package org.forgerock.openig.audit.internal;

import static org.mockito.Mockito.*;

import org.forgerock.openig.audit.AuditEventListener;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ForwardingAuditSystemTest {

    @Mock
    private AuditEventListener listener;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldForwardToRegisteredListeners() throws Exception {
        ForwardingAuditSystem system = new ForwardingAuditSystem();
        system.registerListener(listener);
        system.onAuditEvent(null);
        verify(listener).onAuditEvent(null);
    }

    @Test
    public void shouldSupportListenerUnRegistration() throws Exception {
        ForwardingAuditSystem system = new ForwardingAuditSystem();
        system.registerListener(listener);
        system.onAuditEvent(null);
        system.unregisterListener(listener);
        system.onAuditEvent(null);

        // Should only be called once
        verify(listener).onAuditEvent(null);
    }
}
