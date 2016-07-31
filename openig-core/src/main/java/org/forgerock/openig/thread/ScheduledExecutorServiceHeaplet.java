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

import static org.forgerock.json.JsonValueFunctions.duration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heaplet for building {@literal ScheduledExecutorService} instances.
 *
 * <p>Creates a thread pool that can schedule commands to run after a given delay, or to execute periodically.
 *
 * <p>Reference:
 * <pre>
 *     {@code
 *     {
 *         "type": "ScheduledExecutorService",
 *         "config": {
 *             "corePoolSize":  integer > 0 [ OPTIONAL - default to 1 (will grow as needed)]
 *             "gracefulStop":  boolean     [ OPTIONAL - default to true (all running jobs will complete)]
 *             "gracePeriod" :  duration    [ OPTIONAL - default to '10 second']
 *         }
 *     }
 *     }
 * </pre>
 *
 * Usage:
 * <pre>
 *     {@code
 *     {
 *         "type": "ScheduledExecutorService",
 *         "config": {
 *             "corePoolSize": 42 // defaults to 1 (will grow as needed), only positive and non-zero
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
 * <pre>
 *     {@code
 *     {
 *         "gracefulStop": false // defaults to true
 *     }
 *     }
 * </pre>
 *
 * <p>{@code gracefulPeriod} attribute defines how long the heaplet should wait for jobs to actually terminate properly.
 *
 * <p>Note that this setting is only considered when {@code gracefulStop} is set to {@literal true}.
 *
 * <pre>
 *     {@code
 *     {
 *         "gracePeriod": "20 seconds" // defaults to 10 seconds
 *     }
 *     }
 * </pre>
 *
 * <p>When the period is over, if the executor service is not properly terminated, the heaplet prints a message and
 * exits.
 *
 * <p>Note that all configuration attributes can be defined using static expressions (they can't be resolved against
 * {@code context} or {@code request} objects that are not available at init time).
 *
 * @see ExecutorService#shutdown()
 * @see ExecutorService#shutdownNow()
 * @see ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)
 * @see ExecutorService#isTerminated()
 */
public class ScheduledExecutorServiceHeaplet extends GenericHeaplet {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledExecutorServiceHeaplet.class);

    private JsonValue evaluated;
    private Duration gracePeriod;
    private boolean gracefulStop;

    @Override
    public ExecutorService create() throws HeapException {
        evaluated = config.as(evaluatedWithHeapBindings());
        // Force checks at init time
        gracefulStop = evaluated.get("gracefulStop").defaultTo(true).asBoolean();
        gracePeriod = evaluated.get("gracePeriod").defaultTo("10 seconds").as(duration());

        ScheduledThreadPoolExecutor delegate = new ScheduledThreadPoolExecutor(corePoolSize());
        delegate.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return new MdcScheduledExecutorServiceDelegate(delegate);
    }

    private int corePoolSize() throws HeapException {
        int size = evaluated.get("corePoolSize").defaultTo(1).asInteger();
        if (size <= 0) {
            throw new HeapException("'corePoolSize' can only be a positive (non-zero) value");
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
            // * Does not accept new jobs
            // * Submitted jobs (not yet running) will be drained from the task queue
            // * Running jobs won't be killed
            // * Does not wait for termination
            service.shutdown();

            // Only wait for termination if there is a grace period defined
            if (!gracePeriod.isZero()) {
                try {
                    service.awaitTermination(gracePeriod.getValue(), gracePeriod.getUnit());
                } catch (InterruptedException e) {
                    logger.trace("Termination interrupted, graceful period abandoned");
                }
            }

            if (!service.isTerminated()) {
                logger.info("All tasks in ExecutorService have not completed yet");
            }
        } else {
            // Aggressive shutdown:
            // * Does not accept new jobs
            // * Clear pending queue (will not be executed)
            // * Attempt to kill executing jobs (interruption)
            // * Does not wait for termination
            List<Runnable> jobs = service.shutdownNow();
            if (!jobs.isEmpty()) {
                logger.debug("{} submitted jobs will not be executed", jobs.size());
            }
        }
    }
}
