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

package org.forgerock.openig.audit;

/**
 * An AuditSystem is an OpenIG service that helps to decouple {@link AuditEvent} producers and consumers.
 * The AuditSystem is the primary target for audit notifications generated by decorated heap objects, it can then
 * pre-process them before forwarding them to registered agents ({@link AuditEventListener}).
 * <p>
 * Concrete {@link AuditSystem} implementations could:
 * <ul>
 *     <li>Persist the notifications</li>
 *     <li>Call agents in an asynchronous way (limit the latency in request processing, but consume more CPU)</li>
 *     <li>Pre-filter the notifications</li>
 * </ul>
 */
public interface AuditSystem extends AuditEventListener {
    /**
     * Registers an event listener into this audit system.
     * Once registered, it will receive all events generated by audit decorators hooked in this AuditSystem instance.
     *
     * @param listener registered listener
     * @see ConditionalAuditEventListener
     */
    void registerListener(AuditEventListener listener);

    /**
     * Un-registers an event listener from this audit system.
     * Once un-registered, it will not receive any event anymore.
     *
     * @param listener registered listener to un-register
     * @see ConditionalAuditEventListener
     */
    void unregisterListener(AuditEventListener listener);
}
