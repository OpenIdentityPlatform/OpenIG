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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.log;

import org.forgerock.openig.heap.Name;

/**
 * A sink that discards all log entries. GNDN.
 * @deprecated Will be replaced by SLF4J / Logback in OpenIG 5.0
 */
@Deprecated
public class NullLogSink implements LogSink {

    @Override
    public void log(LogEntry entry) {
        // ignore
    }

    @Override
    public boolean isLoggable(Name source, LogLevel level) {
        return false;
    }
}
