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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.handler.router.Files.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.HashMap;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
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
        final HashMap<File, Long> snapshot = new HashMap<File, Long>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        observer.scan(listener);

        File jsonFile = new File(directory, "new-file.json");
        verify(listener).onChanges(argThat(containsAddedFile(jsonFile)));
        assertThat(snapshot).containsKey(jsonFile);
    }

    @Test
    public void testNonJsonFilesAreIgnored() throws Exception {
        final File directory = getTestResourceDirectory("empty");
        final HashMap<File, Long> snapshot = new HashMap<File, Long>();
        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        observer.scan(listener);

        verifyZeroInteractions(listener);
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testRemovedFilesAreDetectedAndRemovedFromSnapshot() throws Exception {
        final File directory = getTestResourceDirectory("empty");
        final HashMap<File, Long> snapshot = new HashMap<File, Long>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        observer.scan(listener);

        verify(listener).onChanges(argThat(containsRemovedFile(jsonFile)));
        assertThat(snapshot).isEmpty();
    }

    @Test
    public void testModifiedFilesAreDetected() throws Exception {
        final File directory = getTestResourceDirectory("modified");
        final HashMap<File, Long> snapshot = new HashMap<File, Long>();
        // Mimic a file that was previously detected (so it appears in the snapshot map)
        File jsonFile = new File(directory, "new-file.json");
        snapshot.put(jsonFile, 0L);

        DirectoryMonitor observer = new DirectoryMonitor(directory, snapshot);
        observer.scan(listener);

        verify(listener).onChanges(argThat(containsModifiedFile(jsonFile)));
        assertThat(snapshot).contains(entry(jsonFile, jsonFile.lastModified()));
    }

    // Harmcrest matcher for fluent API usage

    static enum Within {
        ADDED, MODIFIED, REMOVED
    }

    private Matcher<FileChangeSet> containsAddedFile(final File file) {
        return new FileInChangeSetMatcher(file, Within.ADDED);
    }

    private Matcher<FileChangeSet> containsModifiedFile(final File file) {
        return new FileInChangeSetMatcher(file, Within.MODIFIED);
    }

    private Matcher<FileChangeSet> containsRemovedFile(final File file) {
        return new FileInChangeSetMatcher(file, Within.REMOVED);
    }

    private static class FileInChangeSetMatcher extends BaseMatcher<FileChangeSet> {

        private final File file;
        private final Within within;

        public FileInChangeSetMatcher(final File file, final Within within) {
            super();
            this.file = file;
            this.within = within;
        }

        @Override
        public boolean matches(final Object o) {
            FileChangeSet set = (FileChangeSet) o;
            switch (within) {
            case ADDED:
                return set.getAddedFiles().contains(file);
            case REMOVED:
                return set.getRemovedFiles().contains(file);
            case MODIFIED:
                return set.getModifiedFiles().contains(file);
            }

            return false;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("FileChangeSet did not contains " + file + " within " + within + " files");
        }
    }
}
