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

import static java.util.concurrent.TimeUnit.*;

/**
 * Records elapsed time in a log in milliseconds.
 * @deprecated Will be replaced by SLF4J / Logback in OpenIG 5.0
 */
@Deprecated
public class LogTimer {

    /** The time that the timer was started. */
    private long started = Long.MIN_VALUE; // indicates the timer has not been started

    /** The logger to record log entries to. */
    private final Logger logger;

    /** The event (within the source) that is being timed. */
    private final String event;

    /** The log level to log timer events with. */
    private final LogLevel level;

    private long paused = Long.MIN_VALUE; // indicates the timer has not been paused

    /** Time spend between consecutive pause() and resume() calls. */
    private long ignorable;

    /**
     * Constructs a new timer with a logging level of {@link LogLevel#STAT STAT}.
     *
     * @param logger the sink to record timer log entries to.
     */
    public LogTimer(Logger logger) {
        this(logger, LogLevel.STAT);
    }

    /**
     * Constructs a new timer to log events at a specified logging level.
     *
     * @param logger the sink to record timer log entries to.
     * @param level the logging level to record timer log entries with.
     */
    public LogTimer(Logger logger, LogLevel level) {
        this(logger, level, null);
    }

    /**
     * Constructs a new timer to log events of a specific type at a specific logging level.
     *
     * @param logger the sink to record timer log entries to.
     * @param level the logging level to record timer log entries with.
     * @param event the event being timed.
     */
    public LogTimer(Logger logger, LogLevel level, String event) {
        // avoid call to nanoTime improbably yielding Long.MIN_VALUE
        System.nanoTime();
        this.logger = logger;
        this.event = event;
        this.level = level;
    }

    /**
     * Starts the timer. Records a log entry indicating the timer has been started.
     *
     * @return this timer instance.
     */
    public LogTimer start() {
        if (logger != null) {
            logger.log(logger.createEntry("started", level, "Started"));
        }
        started = System.nanoTime();
        return this;
    }

    /**
     * Stops the timer and records the elapsed time(s) in a metric.
     */
    public void stop() {
        long stopped = System.nanoTime();
        if (logger != null && started != Long.MIN_VALUE) {
            long elapsed = MILLISECONDS.convert(stopped - started, NANOSECONDS);
            LogMetric metric = new LogMetric(elapsed, "ms");
            logger.log(logger.createEntry("elapsed", level, "Elapsed time: " + metric, metric));
            if (ignorable > 0) {
                // Log the elapsed time inside an object (without the summed pause times)
                long ignoredMs = MILLISECONDS.convert(ignorable, NANOSECONDS);
                LogMetric within = new LogMetric(elapsed - ignoredMs, "ms");
                logger.log(logger.createEntry("elapsed-within",
                                              level,
                                              "Elapsed time (within the object): " + within,
                                              within));
            }
        }
    }

    /**
     * Mark the beginning of a pause in the current timer.
     * Will only do something when:
     * <ul>
     *     <li>the timer has been started</li>
     *     <li>the timer is <b>not</b> currently paused</li>
     * </ul>
     *
     * @return this timer
     */
    public LogTimer pause() {
        // Ensure the timer has been started
        if (started != Long.MIN_VALUE) {
            // Ignore if pause is called multiple times without resume
            if (paused == Long.MIN_VALUE) {
                paused = System.nanoTime();
            }
        }
        return this;
    }

    /**
     * Mark the end of a pause in the current timer (sum up all of the pauses lengths).
     * Will only do something when the timer is currently paused. It will also reset the pause beginning marker
     * to its default value in order to allow multiple pause/resume calls.
     *
     * @return this timer
     */
    public LogTimer resume() {
        // Ensure the timer has been paused
        if (paused != Long.MIN_VALUE) {
            long resumed = System.nanoTime();
            ignorable += (resumed - paused);
            paused = Long.MIN_VALUE;
        }
        return this;
    }
}
