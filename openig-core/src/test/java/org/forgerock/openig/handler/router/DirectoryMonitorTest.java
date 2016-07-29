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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.openig.handler.router.Files.getTestResourceDirectory;

import java.io.File;
import java.util.HashMap;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DirectoryMonitorTest {

    @Mock
    private FileChangeListener listener;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAddedFilesAreDetectedAndStoredInSnapshot() throws Exception {
        final File directory = getTestResourceDirectory("added");
        final HashMap<File, Long> snapshot = new HashMap<>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.scan();

        File jsonFile = new File(directory, "new-file.json");
        assertThat(fileChangeSet.getAddedFiles()).contains(jsonFile);
        assertThat(snapshot).containsKey(jsonFile);
    }

    @Test
    public void testNonJsonFilesAreIgnored() throws Exception {
        final File directory = getTestResourceDirectory("empty");
        final HashMap<File, Long> snapshot = new HashMap<>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);

        assertThat(observer.scan().isEmpty()).isTrue();
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testRemovedFilesAreDetectedAndRemovedFromSnapshot() throws Exception {
        final File directory = getTestResourceDirectory("empty");
        final HashMap<File, Long> snapshot = new HashMap<>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.scan();

        assertThat(fileChangeSet.getRemovedFiles()).contains(jsonFile);
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testModifiedFilesAreDetected() throws Exception {
        final File directory = getTestResourceDirectory("modified");
        final HashMap<File, Long> snapshot = new HashMap<>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.scan();

        assertThat(fileChangeSet.getModifiedFiles()).contains(jsonFile);
        assertThat(snapshot).contains(entry(jsonFile, jsonFile.lastModified()));
    }
}
