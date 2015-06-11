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

package org.forgerock.openig.audit.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.openig.audit.AuditEvent;
import org.forgerock.openig.audit.AuditEventListener;
import org.forgerock.openig.audit.AuditSystem;

/**
 * Forward the {@link AuditEvent}s to the registered audit listeners (final consumers of the events).
 * <p>
 * No storage is done in this implementation for later connected agents notifications or for post-processing of
 * emitted events.
 */
public class ForwardingAuditSystem implements AuditSystem {

    private final List<AuditEventListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onAuditEvent(final AuditEvent event) {
        for (AuditEventListener listener : listeners) {
            listener.onAuditEvent(event);
        }
    }

    @Override
    public void registerListener(final AuditEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterListener(final AuditEventListener listener) {
        listeners.remove(listener);
    }
}
