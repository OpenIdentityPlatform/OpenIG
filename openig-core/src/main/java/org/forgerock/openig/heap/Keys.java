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
package org.forgerock.openig.heap;

import org.forgerock.openig.audit.AuditSystem;
import org.forgerock.openig.audit.decoration.AuditDecorator;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.decoration.baseuri.BaseUriDecorator;
import org.forgerock.openig.decoration.capture.CaptureDecorator;
import org.forgerock.openig.decoration.timer.TimerDecorator;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogSink;

/**
 * Define here the constants that can be used as Heap's keys.
 */
public final class Keys {

    /**
     * Key to retrieve a {@link AuditDecorator} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String AUDIT_HEAP_KEY = "audit";

    /**
     * Key to retrieve a default {@link AuditSystem} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
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
     * Key to retrieve an {@link Environment} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String ENVIRONMENT_HEAP_KEY = "Environment";

    /**
     * Key to retrieve an {@link HttpClient} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String HTTP_CLIENT_HEAP_KEY = "HttpClient";

    /**
     * Key to retrieve a {@link LogSink} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String LOGSINK_HEAP_KEY = "LogSink";

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

    private Keys() {
        // Prevents from instantiating.
    }
}
