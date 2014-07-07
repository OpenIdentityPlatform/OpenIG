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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.log;

/**
 * Receives and handles log entries.
 */
public interface LogSink {

    /**
     * Key to retrieve a {@link LogSink} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String LOGSINK_HEAP_KEY = "LogSink";

    /**
     * Logs an entry.
     *
     * @param entry the entry to be logged.
     */
    void log(LogEntry entry);

    /**
     * Returns {@code true} if the entry may be logged based on its source and/or level. This
     * does not guarantee that the entry will in fact be logged.
     *
     * @param source the object and/or event related to the log entry.
     * @param level the logging level of the log entry.
     * @return {@code true} if the entry may be logged.
     */
    boolean isLoggable(String source, LogLevel level);
}
