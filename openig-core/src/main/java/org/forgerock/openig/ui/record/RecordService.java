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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.ui.record;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static org.forgerock.util.Reject.checkNotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.util.annotations.VisibleForTesting;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File-based {@link Record} storage service.
 *
 * <p>It is configured to record 1 file per record in a given {@code directory}.
 *
 * <p>Record's identifiers are generated from {@link UUID} when created. This UUID is then used
 * as the name (with the {@literal .json} suffix) of the file where content will be persisted.
 *
 * <p>Content is stored without any change (it won't include {@literal _id} or {@literal _rev}) inside of the file.
 * Content can be any generic JSON structure: primitives, null, array or object.
 *
 * <p>Note that the following structure is used:
 *
 * <pre>
 *     {@code {
 *         "id": "43c7d754-efc7-4838-a5da-782dd9c360c4",
 *         "rev": "1ce6ce0e-387f-4ebe-8450-9d21ab5d71e5",
 *         "content": {
 *             "key": [ 42 ]
 *         }
 *     }
 *     }
 * </pre>
 *
 * <p>Record's revision is based on a UUID that change with each update. That provides a basic MVCC support.
 *
 * <p>Note that we don't provide full MVCC support: we don't keep older revisions of the records
 * to allow concurrent reads on different versions.
 *
 * <p>As we don't expect high concurrency and high performance is not a requirement, data consistency is guaranteed
 * with the usage of {@literal synchronized} methods.
 */
public class RecordService {

    @VisibleForTesting
    static final ObjectMapper MAPPER = new ObjectMapper().registerModules(new Json.JsonValueModule());

    private static final String DOT_JSON = ".json";

    private static final FilenameFilter END_WITH_DOT_JSON = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(DOT_JSON);
        }
    };

    private final File directory;

    /**
     * Creates a {@link RecordService} that will record resources in the given directory.
     * @param directory storage directory
     * @throws IOException when the given {@code directory} is not a directory and/or cannot be created
     */
    public RecordService(File directory) throws IOException {
        this.directory = checkNotNull(directory);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create directory " + directory);
        }
    }

    /**
     * Store the given content on disk and returns a {@link Record} that represents the new resource.
     * @param content content to be stored (cannot be {@code null})
     * @return a new {@link Record} with a newly generated UUID
     * @throws IOException if resource storage failed
     */
    public synchronized Record create(JsonValue content) throws IOException {
        Record record = new Record(newUuid(), newUuid(), checkNotNull(content));
        saveRecord(record);
        return record;
    }

    /**
     * Find a {@link Record} with the given {@code id}, returns {@code null} if not found.
     * @param id record's identifier
     * @return the found record or {@code null} if not found
     * @throws IOException if record loading from file has failed
     */
    public synchronized Record find(String id) throws IOException {
        File resource = new File(directory, resourceFilename(id));
        if (resource.isFile()) {
            return loadRecord(resource);
        }
        return null;
    }

    /**
     * Deletes a {@link Record} identified by {@code id} (possibly at a given {@code revision}),
     * returns {@code null} if not found.
     * @param id record to delete identifier
     * @param revision expected revision (can be set to {@code null} if revision has to be ignored)
     * @return the found record or {@code null} if not found
     * @throws IOException if deleted record loading from file has failed
     * @throws RecordException if stored record has a revision that does not match the expected one
     */
    public synchronized Record delete(String id, String revision) throws IOException, RecordException {
        File resource = new File(directory, resourceFilename(id));
        if (resource.isFile()) {
            // If revision is set and current revision does not match, fail-fast with RecordException
            Record candidate = loadRecord(resource);
            if (!Objects.equals(revision, candidate.getRevision())) {
                throw new RecordException(format("Expected revision '%s' of record '%s' not found.",
                                                 revision,
                                                 id));

            }
            resource.delete();
            return candidate;
        }
        return null;
    }

    /**
     * Update the {@link Record} identified by {@code id} (possibly at a given {@code revision})
     * with the provided {@code content}.
     * The backing file will first be deleted and then re-created with the new {@code content}.
     * @param id record to update
     * @param revision expected revision (can be set to {@code null} if revision has to be ignored)
     * @param newContent new content (cannot be {@code null})
     * @return the updated record or {@code null} is resource is not found
     * @throws IOException if updated record deletion or re-creation failed
     * @throws RecordException if stored record has a revision that does not match the expected one
     */
    public synchronized Record update(String id, String revision, JsonValue newContent)
            throws IOException, RecordException {
        // check if resource exists
        if (delete(id, revision) == null) {
            return null;
        }
        // Create updated record with new revision
        Record updated = new Record(id, newUuid(), checkNotNull(newContent));
        saveRecord(updated);
        return updated;
    }

    /**
     * List all persisted records from the file system.
     * @return all persisted records
     * @throws IOException if one of the record cannot be loaded
     */
    public synchronized Set<Record> listAll() throws IOException {
        File[] files = directory.listFiles(endingWithDotJson());

        if (files == null) {
            return emptySet();
        }

        Set<Record> records = new HashSet<>();
        for (File file : files) {
            records.add(loadRecord(file));
        }
        return records;
    }

    private void saveRecord(Record record) throws IOException {
        File resource = new File(directory, resourceFilename(record.getId()));
        MAPPER.writeValue(resource, record);
    }

    private static Record loadRecord(File resource) throws IOException {
        return MAPPER.readValue(resource, Record.class);
    }

    @VisibleForTesting
    static String resourceFilename(String id) {
        return id + DOT_JSON;
    }

    private static FilenameFilter endingWithDotJson() {
        return END_WITH_DOT_JSON;
    }

    private static String newUuid() {
        return UUID.randomUUID().toString();
    }
}
