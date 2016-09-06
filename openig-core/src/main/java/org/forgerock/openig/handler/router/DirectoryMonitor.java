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

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.util.Arrays.asList;
import static org.forgerock.json.JsonValue.json;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.util.annotations.VisibleForTesting;

import com.fasterxml.jackson.databind.ObjectMapper;

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
class DirectoryMonitor {

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper().registerModules(new Json.JsonValueModule());
        MAPPER.configure(INDENT_OUTPUT, true);
    }

    /**
     * Monitored directory.
     */
    private final File directory;

    /**
     * Snapshot of the directory content. It maps the {@link File} to its {@linkplain File#lastModified() last modified}
     * value. It represents the currently "managed" files.
     */
    private final Map<File, Long> snapshot;

    private Lock lock = new ReentrantLock();

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
     * @param directory
     *         a non-{@literal null} directory (it may or may not exist) to monitor
     * @param snapshot
     */
    public DirectoryMonitor(final File directory, final Map<File, Long> snapshot) {
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
            latest = new ArrayList<>(asList(directory.listFiles(jsonFiles())));
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

    void store(String routeId, JsonValue routeConfig) throws IOException {
        lock.lock();
        try {
            File routeFile = routeFile(routeId);
            // Creates intermediate directories if required
            File parent = routeFile.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Cannot create directories for file " + routeFile);
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(routeFile),
                                                                    StandardCharsets.UTF_8)) {
                writer.write(MAPPER.writeValueAsString(routeConfig.getObject()));
            }
            // Update the snapshot so it is not detected during the next scan
            snapshot.put(routeFile, routeFile.lastModified());
        } finally {
            lock.unlock();
        }
    }

    void delete(String routeId) {
        lock.lock();
        try {
            File routeFile = routeFile(routeId);
            if (routeFile.delete()) {
                // Update the snapshot so it is not detected during the next scan
                snapshot.remove(routeFile);
            }
        } finally {
            lock.unlock();
        }
    }

    JsonValue read(String routeId) throws IOException {
        lock.lock();
        try {
            File routeFile = routeFile(routeId);
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(routeFile),
                                                                  StandardCharsets.UTF_8)) {
                return json(MAPPER.readValue(reader, Object.class));
            }
        } finally {
            lock.unlock();
        }
    }

    private File routeFile(String routeId) {
        return new File(directory, routeId + ".json");
    }
}
