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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensure that the directoryMonitor {@link DirectoryScanner} will be executed only once.
 *
 * @since 2.2
 */
class OnlyOnceDirectoryScanner implements DirectoryScanner {

    private final DirectoryMonitor directoryMonitor;

    /**
     * Contains {@literal false} if it has never been executed before.
     * Its value is set to {@literal true} at first execution.
     */
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private FileChangeListener listener;

    /**
     * Builds a OnlyOnceDirectoryScanner wrapping the given scanner.
     * @param directoryMonitor the directoryMonitor to scan
     */
    public OnlyOnceDirectoryScanner(final DirectoryMonitor directoryMonitor) {
        this.directoryMonitor = directoryMonitor;
    }

    @Override
    public void register(final FileChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        // If it has not already been executed
        if (executed.compareAndSet(false, true)) {
            if (listener != null) {
                listener.onChanges(directoryMonitor.scan());
            }
        }
    }

    @Override
    public void stop() {
    }
}
