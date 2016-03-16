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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.thread;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.forgerock.openig.util.JsonValues.asBoolean;
import static org.forgerock.openig.util.JsonValues.asDuration;
import static org.forgerock.openig.util.JsonValues.asInteger;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.time.Duration;

/**
 * Heaplet for building {@literal ScheduledThreadPool} instances.
 *
 * <p>Creates a thread pool that can schedule commands to run after a given delay, or to execute periodically.
 *
 * <p>Reference:
 * <pre>
 *     {@code
 *     {
 *         "type": "ScheduledThreadPool",
 *         "config": {
 *             "corePoolSize":  integer > 0 [ OPTIONAL - default to 0 (will grow as needed)]
 *             "gracefulStop":  boolean     [ OPTIONAL - default to false (actively try to kill jobs)]
 *             "gracePeriod" :  duration    [ OPTIONAL - default to '0 second' (no wait)]
 *         }
 *     }
 *     }
 * </pre>
 *
 * Usage:
 * <pre>
 *     {@code
 *     {
 *         "type": "ScheduledThreadPool",
 *         "config": {
 *             "corePoolSize": 42 // defaults to 0 (will grow as needed), only positive
 *         }
 *     }
 *     }
 * </pre>
 *
 * <p>This class supports graceful stop.
 *
 * {@code gracefulStop} is a setting that allows a thread pool to wind down nicely without
 * killing aggressively running (and submitted) jobs.
 *
 * <p>Note that this setting is independent of the {@code gracePeriod}: it's not blocking until jobs have finished.
 * <pre>
 *     {@code
 *     {
 *         "gracefulStop": true // defaults to false
 *     }
 *     }
 * </pre>
 *
 * <p>{@code gracefulPeriod} attribute defines how long the heaplet should wait for jobs to actually terminate properly.
 * When the period is over, if the executor service is not properly terminated, the heaplet prints a message and exits.
 *
 * <pre>
 *     {@code
 *     {
 *         "gracePeriod": "20 seconds" // defaults to 0 seconds (no wait)
 *     }
 *     }
 * </pre>
 *
 * Note that all configuration attributes can be defined using static expressions (they can't be resolved against
 * {@code context} or {@code request} objects that are not available at init time).
 *
 * @see java.util.concurrent.Executors#newScheduledThreadPool(int)
 * @see ExecutorService#shutdown()
 * @see ExecutorService#shutdownNow()
 * @see ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)
 * @see ExecutorService#isTerminated()
 */
public class ScheduledThreadPoolHeaplet extends GenericHeaplet {

    private Duration gracePeriod;
    private boolean gracefulStop;

    @Override
    public ExecutorService create() throws HeapException {
        // Force checks at init time
        gracefulStop = asBoolean(config.get("gracefulStop").defaultTo(false));
        gracePeriod = asDuration(config.get("gracePeriod").defaultTo("0 second"));
        return newScheduledThreadPool(corePoolSize());
    }

    private int corePoolSize() throws HeapException {
        int size = asInteger(config.get("corePoolSize").defaultTo(0));
        if (size < 0) {
            throw new HeapException("'corePoolSize' can only be a positive value");
        }
        return size;
    }

    @Override
    public void destroy() {
        super.destroy();
        ExecutorService service = (ExecutorService) this.object;
        if (service == null) {
            return;
        }

        if (gracefulStop) {
            // Graceful shutdown:
            // * Does not accepts new jobs
            // * Submitted jobs will be executed, not killed
            // * Does not wait for termination
            service.shutdown();
        } else {
            // Aggressive shutdown:
            // * Does not accepts new jobs
            // * Attempt to kill executing jobs (interruption)
            // * Clear pending queue (will not be executed)
            // * Does not wait for termination
            List<Runnable> jobs = service.shutdownNow();
            if (!jobs.isEmpty()) {
                logger.debug(format("%d submitted jobs will not be executed", jobs.size()));
            }
        }

        // Only wait for termination if there is a grace period defined
        if (!gracePeriod.isZero()) {
            try {
                service.awaitTermination(gracePeriod.getValue(), gracePeriod.getUnit());
            } catch (InterruptedException e) {
                logger.trace("Termination interrupted, graceful period abandoned");
            }
        }

        if (!service.isTerminated()) {
            logger.warning("Executor service did not terminate properly");
        }
    }
}
