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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trigger a directory scan if a given amount of time has elapsed since last scan.
 * Only 1 thread at a time can trigger the new scan.
 */
class PeriodicDirectoryScanner implements DirectoryScanner {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicDirectoryScanner.class);

    /**
     * Delegate.
     */
    private final DirectoryMonitor directoryMonitor;

    /**
     * Scheduled Executor Service.
     */
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Delay between 2 directory scans (expressed in milliseconds).
     */
    private long scanInterval = MILLISECONDS.convert(10, SECONDS);

    private ScheduledFuture<?> scheduledCommand;
    private FileChangeListener listener;

    /**
     * Builds a new scanner that will delegates to the given {@link DirectoryScanner}.
     * @param directoryMonitor real scanner
     * @param scheduledExecutorService executor to schedule scans
     */
    public PeriodicDirectoryScanner(final DirectoryMonitor directoryMonitor,
                                    final ScheduledExecutorService scheduledExecutorService) {
        this.directoryMonitor = directoryMonitor;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * Sets the delay between 2 directory scans (expressed in milliseconds).
     * @param scanInterval the delay between 2 directory scans (expressed in milliseconds).
     */
    public void setScanInterval(final long scanInterval) {
        if (scanInterval <= 0) {
            throw new IllegalArgumentException(
                    "interval is expressed in milliseconds and cannot be less or equal to zero");
        }
        this.scanInterval = scanInterval;
    }

    @Override
    public void register(final FileChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        Runnable command = new Runnable() {
            @Override
            public void run() {
                try {
                    FileChangeSet fileChangeSet = directoryMonitor.scan();
                    if (fileChangeSet == null || fileChangeSet.isEmpty()) {
                        return;
                    }
                    listener.onChanges(fileChangeSet);
                } catch (Exception e) {
                    logger.error("An error occurred while scanning periodically", e);
                }
            }
        };
        scheduledCommand = scheduledExecutorService.scheduleAtFixedRate(command,
                                                                        0,
                                                                        scanInterval,
                                                                        MILLISECONDS);
    }

    @Override
    public void stop() {
        scheduledCommand.cancel(true);
    }
}
