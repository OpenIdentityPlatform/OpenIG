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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import java.util.concurrent.Semaphore;

import org.forgerock.util.time.TimeService;

/**
 * Trigger a directory scan if a given amount of time has elapsed since last scan.
 * Only 1 thread at a time can trigger the new scan.
 */
class PeriodicDirectoryScanner implements DirectoryScanner {

    /**
     * Default scan interval.
     */
    public static final int TEN_SECONDS = 10;

    /**
     * Delegate.
     */
    private final DirectoryScanner delegate;

    /**
     * Used to ensure that only 1 thread can trigger the scan.
     */
    private final Semaphore semaphore = new Semaphore(1);

    /**
     * Timestamp at which the directory scan was last triggered.
     */
    private volatile long lastScan = 0;

    /**
     * Time service.
     */
    private final TimeService time;

    /**
     * Delay between 2 directory scans (expressed in milliseconds).
     */
    private int scanInterval = TEN_SECONDS * 1000;

    /**
     * Builds a new scanner that will delegates to the given {@link DirectoryScanner}.
     * @param delegate real scanner
     * @param time time service
     */
    public PeriodicDirectoryScanner(final DirectoryScanner delegate, final TimeService time) {
        this.delegate = delegate;
        this.time = time;
    }

    /**
     * Sets the delay between 2 directory scans (expressed in milliseconds).
     * @param scanInterval the delay between 2 directory scans (expressed in milliseconds).
     */
    public void setScanInterval(final int scanInterval) {
        if (scanInterval <= 0) {
            throw new IllegalArgumentException(
                    "interval is expressed in milliseconds and cannot be less or equal to zero"
            );
        }
        this.scanInterval = scanInterval;
    }

    @Override
    public void scan(final FileChangeListener listener) {
        if (time.since(lastScan) >= scanInterval) {
            // Ensure only 1 Thread enter that block at a given time
            if (semaphore.tryAcquire()) {
                try {
                    lastScan = time.now();
                    delegate.scan(listener);
                } finally {
                    semaphore.release();
                }
            }
        }
    }
}
