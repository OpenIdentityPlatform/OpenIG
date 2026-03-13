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
 * Copyright 2026 3A Systems LLC.
 */

package org.forgerock.openig.handler.router;

import org.forgerock.util.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Arrays.asList;

public abstract class AbstractDirectoryMonitor {
    /**
     * Monitored directory.
     */
    protected final File directory;

    /**
     * Snapshot of the directory content. It maps the {@link File} to its {@linkplain File#lastModified() last modified}
     * value. It represents the currently "managed" files.
     */
    protected final Map<File, Long> snapshot;

    protected final Lock lock = new ReentrantLock();

    /**
     * Builds a new monitor watching for changes in the given {@literal directory} that will notify the given listener.
     * This constructor is intended for test cases where it's useful to provide an initial state under control.
     * @param directory
     *         a non-{@literal null} directory (it may or may not exist) to monitor
     * @param snapshot
     *         initial state of the snapshot
     */
    public AbstractDirectoryMonitor(final File directory, final Map<File, Long> snapshot) {
        this.directory = directory;
        this.snapshot = snapshot;
    }

    /**
     * Monitor the directory and notify the listener.
     * @param listener the listener to notify about the changes
     */
    public void monitor(FileChangeListener listener) {
        if (lock.tryLock()) {
            try {
                FileChangeSet fileChangeSet = createFileChangeSet();
                if (fileChangeSet.isEmpty()) {
                    // If there is no change to propagate, simply return
                    return;
                }
                // Invoke listeners
                listener.onChanges(fileChangeSet);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Returns a snapshot of the changes compared to the previous scan.
     * @return a snapshot of the changes compared to the previous scan.
     */
    @VisibleForTesting
    FileChangeSet createFileChangeSet() {
        // Take a snapshot of the current directory
        List<File> latest = Collections.emptyList();
        if (directory.isDirectory()) {
            latest = new ArrayList<>(asList(directory.listFiles(getFileFilter())));
        }

        // Detect added files
        // (in latest but not in known)
        Set<File> added = new HashSet<>();
        for (File candidate : new ArrayList<>(latest)) {
            if (!snapshot.containsKey(candidate)) {
                added.add(candidate);
                latest.remove(candidate);
            }
        }

        // Detect removed files
        // (in known but not in latest)
        Set<File> removed = new HashSet<>();
        for (File candidate : new ArrayList<>(snapshot.keySet())) {
            if (!latest.contains(candidate)) {
                removed.add(candidate);
                snapshot.remove(candidate);
            }
        }

        // Detect modified files
        // Now, latest and known list should have the same Files inside
        Set<File> modified = new HashSet<>();
        for (File candidate : latest) {
            long lastModified = snapshot.get(candidate);
            if (lastModified < candidate.lastModified()) {
                // File has changed since last check
                modified.add(candidate);
                snapshot.put(candidate, candidate.lastModified());
            }
        }

        // Append the added files to the known list for next processing step
        for (File file : added) {
            // Store their last modified value
            snapshot.put(file, file.lastModified());
        }

        return new FileChangeSet(directory, added, modified, removed);
    }

    protected abstract FileFilter getFileFilter();

}
