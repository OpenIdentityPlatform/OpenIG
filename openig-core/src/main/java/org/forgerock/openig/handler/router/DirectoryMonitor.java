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

import static java.util.Arrays.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link DirectoryMonitor} monitors a given directory. It watches the direct content (changes inside
 * children directories will not trigger any notifications) of the given directory, filtering only {@literal *.json}
 * files.
 * <p>
 * It reacts to the following events:
 * <ul>
 *     <li>Added Files: Compared to the last snapshot, a file was created.</li>
 *     <li>Removed Files: Compared to the last snapshot, a file was deleted.</li>
 *     <li>Modified Files: Compared to the last snapshot, a file has been changed externally.</li>
 * </ul>
 *
 * @see FileChangeListener
 * @since 2.2
 */
class DirectoryMonitor implements DirectoryScanner {

    /**
     * Monitored directory.
     */
    private final File directory;

    /**
     * Snapshot of the directory content. It maps the {@link File} to its {@linkplain File#lastModified() last modified}
     * value. It represents the currently "managed" files.
     */
    private final Map<File, Long> snapshot;

    /**
     * Builds a new monitor watching for changes in the given {@literal directory} that will notify the given listener.
     * It starts with an empty snapshot (at first run, all discovered files will be considered as new).
     *
     * @param directory
     *         a non-{@literal null} directory (it may or may not exists) to monitor
     */
    public DirectoryMonitor(final File directory) {
        this(directory, new HashMap<File, Long>());
    }

    /**
     * Builds a new monitor watching for changes in the given {@literal directory} that will notify the given listener.
     * This constructor is intended for test cases where it's useful to provide an initial state under control.
     *
     * @param directory
     *         a non-{@literal null} directory (it may or may not exists) to monitor
     * @param snapshot
     *         initial state of the snapshot
     */
    public DirectoryMonitor(final File directory,
                            final Map<File, Long> snapshot) {
        this.directory = directory;
        this.snapshot = snapshot;
    }

    @Override
    public void scan(final FileChangeListener listener) {

        // Take a snapshot of the current directory
        List<File> latest = Collections.emptyList();
        if (directory.isDirectory()) {
            latest = new ArrayList<File>(asList(directory.listFiles(jsonFiles())));
        }

        // Detect added files
        // (in latest but not in known)
        Set<File> added = new HashSet<File>();
        for (File candidate : new ArrayList<File>(latest)) {
            if (!snapshot.containsKey(candidate)) {
                added.add(candidate);
                latest.remove(candidate);
            }
        }

        // Detect removed files
        // (in known but not in latest)
        Set<File> removed = new HashSet<File>();
        for (File candidate : new ArrayList<File>(snapshot.keySet())) {
            if (!latest.contains(candidate)) {
                removed.add(candidate);
                snapshot.remove(candidate);
            }
        }

        // Detect modified files
        // Now, latest and known list should have the same Files inside
        Set<File> modified = new HashSet<File>();
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

        // If there is no change to propagate, simply return
        if (added.isEmpty() && removed.isEmpty() && modified.isEmpty()) {
            return;
        }

        // Invoke listeners
        listener.onChanges(new FileChangeSet(directory, added, modified, removed));
    }

    /**
     * Factory method to be used as a fluent {@link FileFilter} declaration.
     *
     * @return a filter for {@literal .json} files
     */
    private static FileFilter jsonFiles() {
        return new FileFilter() {
            @Override
            public boolean accept(final File path) {
                return path.isFile() && path.getName().endsWith(".json");
            }
        };
    }


}
