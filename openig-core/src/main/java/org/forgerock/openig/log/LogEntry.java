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
 * The log entry data structure.
 */
public class LogEntry {
    /** The time of the event being logged (milliseconds since the 1970 epoch). */
    private final long time = System.currentTimeMillis();

    /**
     * The subject and/or event being logged, in hierarchical dot-delimited
     * notation.
     */
    private final String source;

    /** The logging level of the entry. */
    private final LogLevel level;

    /** Human-readable message text, suitable for display in any entry listings. */
    private final String message;

    /** The data being logged or {@code null} if no data. */
    private final Object data;

    LogEntry(String source, LogLevel level, String message) {
        this(source, level, message, null);
    }

    LogEntry(String source, LogLevel level, String message, Object data) {
        this.source = source;
        this.level = level;
        this.message = message;
        this.data = data;
    }

    /**
     * Returns the time of the event being logged (milliseconds since the 1970
     * epoch).
     *
     * @return The time of the event being logged (milliseconds since the 1970
     *         epoch).
     */
    public long getTime() {
        return time;
    }

    /**
     * Returns the subject and/or event being logged, in hierarchical
     * dot-delimited notation.
     *
     * @return The subject and/or event being logged, in hierarchical
     *         dot-delimited notation.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the logging level of the entry.
     *
     * @return The logging level of the entry.
     */
    public LogLevel getLevel() {
        return level;
    }

    /**
     * Returns Human-readable message text, suitable for display in any entry
     * listings.
     *
     * @return Human-readable message text, suitable for display in any entry
     *         listings.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the data being logged or {@code null} if no data.
     *
     * @return The data being logged or {@code null} if no data.
     */
    public Object getData() {
        return data;
    }

}
