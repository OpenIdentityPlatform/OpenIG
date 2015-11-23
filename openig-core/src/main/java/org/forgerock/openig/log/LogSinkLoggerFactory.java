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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.log;

import org.forgerock.openig.heap.Name;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * SLF4J {@link ILoggerFactory} implementation that wraps a {@link LogSink}.
 */
public class LogSinkLoggerFactory implements ILoggerFactory {

    private LogSink logSink = new ConsoleLogSink();

    /**
     * Sets the {@link LogSink} to use. This method is called through reflection avoid any cycling dependency between
     * this module and the core one.
     *
     * @param logSink
     *         where the log statements will be send
     */
    public void setLogSink(final LogSink logSink) {
        this.logSink = logSink;
    }

    @Override
    public Logger getLogger(final String name) {
        return new LogSinkLogger(logSink, Name.of(name));
    }
}
