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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.openig.heap;

import org.forgerock.http.Handler;
import org.forgerock.http.filter.TransactionIdOutboundFilter;
import org.forgerock.http.session.SessionManager;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;
import org.forgerock.util.time.TimeService;

/**
 * Define here the constants that can be used as Heap's keys.
 */
public final class Keys {

    /**
     * Key to retrieve a {@link org.forgerock.http.Filter} instance from the {@link org.forgerock.openig.heap.Heap} of
     * {@literal config.json}.
     */
    public static final String API_PROTECTION_FILTER_HEAP_KEY = "ApiProtectionFilter";

    /**
     * Key to retrieve a
     * {@link org.forgerock.openig.audit.decoration.AuditDecorator} instance
     * from the {@link org.forgerock.openig.heap.Heap}.
     */
    @Deprecated
    public static final String AUDIT_HEAP_KEY = "audit";

    /**
     * Key to retrieve a default
     * {@link org.forgerock.openig.audit.AuditSystem} instance from
     * the {@link org.forgerock.openig.heap.Heap}.
     */
    @Deprecated
    public static final String AUDIT_SYSTEM_HEAP_KEY = "AuditSystem";

    /**
     * Key to retrieve a {@link BaseUriDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String BASEURI_HEAP_KEY = "baseURI";

    /**
     * Key to retrieve a {@link CaptureDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String CAPTURE_HEAP_KEY = "capture";

    /**
     * Key to retrieve a default {@link ClientHandler} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String CLIENT_HANDLER_HEAP_KEY = "ClientHandler";

    /**
     * Key to retrieve the {@link EndpointRegistry} instance dedicated for the current Route's objects
     * from the {@link org.forgerock.openig.heap.Heap}.
     *
     * <p>Objects declared in {@literal config.json} will have a registry pointing to {@literal /openig/system/objects}.
     * <p>Objects declared into routes will have another registry that is dedicated to the host route: {@literal
     * /openig/system/objects/.../[route-name]/objects}.
     *
     * <p>Note that generic heaplets may use their private registry (using their own namespace based on their name)
     * through {@linkplain GenericHeaplet#endpointRegistry() endpointRegistry()}.
     * @see GenericHeaplet#endpointRegistry()
     */
    public static final String ENDPOINT_REGISTRY_HEAP_KEY = "EndpointRegistry";

    /**
     * Key to retrieve an {@link Environment} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String ENVIRONMENT_HEAP_KEY = "Environment";

    /**
     * Key to retrieve ForgeRock {@link ClientHandler} instance from the
     * {@link org.forgerock.openig.heap.Heap}, which chains a
     * {@link TransactionIdOutboundFilter} to a {@link ClientHandler}. This
     * {@link Handler} is used by audit to forward custom audit header.
     */
    public static final String FORGEROCK_CLIENT_HANDLER_HEAP_KEY = "ForgeRockClientHandler";

    /**
     * Key to retrieve a {@link LogSink} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String LOGSINK_HEAP_KEY = "LogSink";

    /**
     * Key to retrieve the default {@link java.util.concurrent.ScheduledExecutorService} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY = "ScheduledExecutorService";

    /**
     * Key to retrieve the default {@link SessionManager} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String SESSION_FACTORY_HEAP_KEY = "Session";

    /**
     * Key to retrieve a {@link TemporaryStorage} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TEMPORARY_STORAGE_HEAP_KEY = "TemporaryStorage";

    /**
     * Key to retrieve a {@link TimerDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TIMER_HEAP_KEY = "timer";

    /**
     * Key to retrieve a {@link TimeService} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TIME_SERVICE_HEAP_KEY = "TimeService";

    /**
     * Key to retrieve a {@link TransactionIdOutboundFilter} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TRANSACTION_ID_OUTBOUND_FILTER_HEAP_KEY = "TransactionIdOutboundFilter";

    private Keys() {
        // Prevents from instantiating.
    }
}
