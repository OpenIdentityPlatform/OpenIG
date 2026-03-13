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
 * Portions copyright 2026 3A Systems LLC
 */

package org.forgerock.openig.handler.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.util.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

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
public class DirectoryMonitor extends AbstractDirectoryMonitor {

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper().registerModules(new Json.JsonValueModule());
        MAPPER.configure(INDENT_OUTPUT, true);
    }

    /**
     * Builds a new monitor watching for changes in the given {@literal directory} that will notify the given listener.
     * It starts with an empty snapshot (at first run, all discovered files will be considered as new).
     *
     * @param directory
     *         a non-{@literal null} directory (it may or may not exists) to monitor
     */
    public DirectoryMonitor(final File directory) {
        super(directory, new HashMap<>());
    }

    /**
     * Builds a new monitor watching for changes in the given {@literal directory} that will notify the given listener.
     * This constructor is intended for test cases where it's useful to provide an initial state under control.
     * @param directory
     *         a non-{@literal null} directory (it may or may not exist) to monitor
     * @param snapshot
     *         initial state of the snapshot
     */
    public DirectoryMonitor(final File directory, final Map<File, Long> snapshot) {
        super(directory, snapshot);
    }



    /**
     * Returns a snapshot of the changes compared to the previous scan.
     * @return a snapshot of the changes compared to the previous scan.
     */
    @VisibleForTesting
    FileChangeSet createFileChangeSet() {
        return super.createFileChangeSet();
    }


    /**
     * Factory method to be used as a fluent {@link FileFilter} declaration.
     *
     * @return a filter for {@literal .json} files
     */
    @Override
    protected FileFilter getFileFilter() {
        return path -> path.isFile() && path.getName().endsWith(".json");
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

    private File routeFile(String routeId) {
        return new File(directory, routeId + ".json");
    }
}
