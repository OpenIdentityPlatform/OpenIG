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

import static org.slf4j.helpers.MessageFormatter.arrayFormat;
import static org.slf4j.helpers.MessageFormatter.format;

import org.forgerock.openig.heap.Name;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;

/**
 * Logger implementation that wraps a {@link LogSink}.
 */
class LogSinkLogger extends MarkerIgnoringBase {

    private final LogSink logSink;
    private final Name name;

    LogSinkLogger(final LogSink sink, final Name name) {
        this.logSink = sink;
        this.name = name;
    }

    @Override
    public String getName() {
        return name.getFullyQualifiedName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logSink.isLoggable(name, LogLevel.TRACE);
    }

    @Override
    public void trace(final String msg) {
        if (isTraceEnabled()) {
            logSink.log(traceEntry(msg));
        }
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (isTraceEnabled()) {
            FormattingTuple tuple = format(format, arg);
            trace(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (isTraceEnabled()) {
            FormattingTuple tuple = format(format, arg1, arg2);
            trace(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (isTraceEnabled()) {
            FormattingTuple tuple = arrayFormat(format, arguments);
            trace(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        if (isTraceEnabled()) {
            logSink.log(traceEntry(msg));
            if (t != null) {
                logSink.log(traceEntry(t));
            }
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return logSink.isLoggable(name, LogLevel.DEBUG);
    }

    @Override
    public void debug(final String msg) {
        if (isDebugEnabled()) {
            logSink.log(debugEntry(msg));
        }
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (isDebugEnabled()) {
            FormattingTuple tuple = format(format, arg);
            debug(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled()) {
            FormattingTuple tuple = format(format, arg1, arg2);
            debug(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (isDebugEnabled()) {
            FormattingTuple tuple = arrayFormat(format, arguments);
            debug(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        if (isDebugEnabled()) {
            logSink.log(debugEntry(msg));
            if (t != null) {
                logSink.log(debugEntry(t));
            }
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return logSink.isLoggable(name, LogLevel.INFO);
    }

    @Override
    public void info(final String msg) {
        if (isInfoEnabled()) {
            logSink.log(infoEntry(msg));
        }
    }

    @Override
    public void info(final String format, final Object arg) {
        if (isInfoEnabled()) {
            FormattingTuple tuple = format(format, arg);
            info(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (isInfoEnabled()) {
            FormattingTuple tuple = format(format, arg1, arg2);
            info(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (isInfoEnabled()) {
            FormattingTuple tuple = arrayFormat(format, arguments);
            info(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void info(final String msg, final Throwable t) {
        if (isInfoEnabled()) {
            logSink.log(infoEntry(msg));
            if (t != null) {
                logSink.log(infoEntry(t));
            }
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return logSink.isLoggable(name, LogLevel.WARNING);
    }

    @Override
    public void warn(final String msg) {
        if (isWarnEnabled()) {
            logSink.log(warnEntry(msg));
        }
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (isWarnEnabled()) {
            FormattingTuple tuple = format(format, arg);
            warn(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (isWarnEnabled()) {
            FormattingTuple tuple = format(format, arg1, arg2);
            warn(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (isWarnEnabled()) {
            FormattingTuple tuple = arrayFormat(format, arguments);
            warn(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        if (isWarnEnabled()) {
            logSink.log(warnEntry(msg));
            if (t != null) {
                logSink.log(warnEntry(t));
            }
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return logSink.isLoggable(name, LogLevel.ERROR);
    }

    @Override
    public void error(final String msg) {
        if (isErrorEnabled()) {
            logSink.log(errorEntry(msg));
        }
    }

    @Override
    public void error(final String format, final Object arg) {
        if (isErrorEnabled()) {
            FormattingTuple tuple = format(format, arg);
            error(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (isErrorEnabled()) {
            FormattingTuple tuple = format(format, arg1, arg2);
            error(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (isErrorEnabled()) {
            FormattingTuple tuple = arrayFormat(format, arguments);
            error(tuple.getMessage(), tuple.getThrowable());
        }
    }

    @Override
    public void error(final String msg, final Throwable t) {
        if (isErrorEnabled()) {
            logSink.log(errorEntry(msg));
            if (t != null) {
                logSink.log(errorEntry(t));
            }
        }
    }

    private LogEntry traceEntry(final String message) {
        return logEntry(LogLevel.TRACE, message);
    }

    private LogEntry debugEntry(final String message) {
        return logEntry(LogLevel.DEBUG, message);
    }

    private LogEntry infoEntry(final String message) {
        return logEntry(LogLevel.INFO, message);
    }

    private LogEntry warnEntry(final String message) {
        return logEntry(LogLevel.WARNING, message);
    }

    private LogEntry errorEntry(final String message) {
        return logEntry(LogLevel.ERROR, message);
    }

    private LogEntry traceEntry(final Throwable throwable) {
        return throwableEntry(LogLevel.TRACE, throwable);
    }

    private LogEntry debugEntry(final Throwable throwable) {
        return throwableEntry(LogLevel.DEBUG, throwable);
    }

    private LogEntry infoEntry(final Throwable throwable) {
        return throwableEntry(LogLevel.INFO, throwable);
    }

    private LogEntry warnEntry(final Throwable throwable) {
        return throwableEntry(LogLevel.WARNING, throwable);
    }

    private LogEntry errorEntry(final Throwable throwable) {
        return throwableEntry(LogLevel.ERROR, throwable);
    }

    private LogEntry throwableEntry(final LogLevel level, final Throwable throwable) {
        return entry("throwable", level, throwable.getMessage(), throwable);
    }

    private LogEntry logEntry(final LogLevel level, final String message) {
        return entry("log", level, message, null);
    }

    private LogEntry entry(final String type, final LogLevel level, final String message, final Object data) {
        return new LogEntry(name, type, level, message, data);
    }
}
