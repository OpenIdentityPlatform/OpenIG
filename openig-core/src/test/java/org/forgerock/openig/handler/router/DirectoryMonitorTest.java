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

import static java.lang.String.format;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.util.Files.contentOf;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.Files.getRelativeDirectory;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.reporters.Files.writeFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "added");
        final HashMap<File, Long> snapshot = new HashMap<>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.createFileChangeSet();

        File jsonFile = new File(directory, "new-file.json");
        assertThat(fileChangeSet.getAddedFiles()).contains(jsonFile);
        assertThat(snapshot).containsKey(jsonFile);
    }

    @Test
    public void testNonJsonFilesAreIgnored() throws Exception {
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "empty");
        final HashMap<File, Long> snapshot = new HashMap<>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);

        assertThat(observer.createFileChangeSet().isEmpty()).isTrue();
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testRemovedFilesAreDetectedAndRemovedFromSnapshot() throws Exception {
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "empty");
        final HashMap<File, Long> snapshot = new HashMap<>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.createFileChangeSet();

        assertThat(fileChangeSet.getRemovedFiles()).contains(jsonFile);
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testModifiedFilesAreDetected() throws Exception {
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "modified");
        final HashMap<File, Long> snapshot = new HashMap<>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        FileChangeSet fileChangeSet = observer.createFileChangeSet();

        assertThat(fileChangeSet.getModifiedFiles()).contains(jsonFile);
        assertThat(snapshot).contains(entry(jsonFile, jsonFile.lastModified()));
    }

    @Test
    public void testListenerIsNotNotifiedOnEmptyChangeSet() throws Exception {
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "empty");
        FileChangeSet fileChangeSet = new FileChangeSet(directory,
                                                        Collections.<File>emptySet(),
                                                        Collections.<File>emptySet(),
                                                        Collections.<File>emptySet());

        DirectoryMonitor observer = spy(new DirectoryMonitor(directory));
        when(observer.createFileChangeSet()).thenReturn(fileChangeSet);

        observer.monitor(listener);

        verifyZeroInteractions(listener);
    }

    @Test
    public void testListenerIsNotNotifiedOnNonEmptyChangeSet() throws Exception {
        final File directory = getRelativeDirectory(DirectoryMonitorTest.class, "empty");
        FileChangeSet fileChangeSet = new FileChangeSet(directory,
                                                        Collections.singleton(new File("foo.json")),
                                                        Collections.<File>emptySet(),
                                                        Collections.<File>emptySet());

        DirectoryMonitor observer = spy(new DirectoryMonitor(directory));
        when(observer.createFileChangeSet()).thenReturn(fileChangeSet);

        observer.monitor(listener);

        verify(listener).onChanges(same(fileChangeSet));
    }

    @DataProvider
    public static Object[][] directories() throws IOException {
        // @Checkstyle:off
        return new Object[][]{
                { newTemporaryFolder() },
                // try with a non-existent directory
                // newTemporaryFolder() creates a new dir at each invocation, so there is no need to do some cleanup
                { new File(newTemporaryFolder(), "does-not-exists-yet") }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "directories")
    public void testStoreArtifact(File folder) throws Exception {
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(folder);

        directoryMonitor.store("foo", json(object(field("foo", "bar"))));

        File[] files = folder.listFiles();
        assertThat(files).hasSize(1);
        assertThat(files[0].getName()).isEqualTo("foo.json");
        assertThat(contentOf(files[0], StandardCharsets.UTF_8))
                .isEqualTo(format("{%n  \"foo\" : \"bar\"%n}"));
    }

    @Test
    public void testDeleteArtifact() throws Exception {
        File folder = newTemporaryFolder();
        writeFile("{ \"foo\": \"bar\" }", new File(folder, "foo.json"));
        DirectoryMonitor directoryMonitor = new DirectoryMonitor(folder);

        directoryMonitor.delete("foo");

        File[] files = folder.listFiles();
        assertThat(files).isEmpty();
    }

    private static File newTemporaryFolder() throws IOException {
        final File folder =  createTempDirectory("temp").toFile();
        folder.deleteOnExit();
        return folder;
    }
}
