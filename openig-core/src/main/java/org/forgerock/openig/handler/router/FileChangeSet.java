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

import static java.util.Collections.*;

import java.io.File;
import java.util.Set;

/**
 * Represents the collections of modifications that have been detected by a {@link DirectoryMonitor}.
 *
 * @since 2.2
 */
class FileChangeSet {

    /**
     * Scanned directory.
     */
    private final File directory;

    /**
     * Set a files that were added.
     */
    private final Set<File> addedFiles;

    /**
     * Set a files that were modified.
     */
    private final Set<File> modifiedFiles;

    /**
     * Set a files that were removed.
     */
    private final Set<File> removedFiles;

    /**
     * Builds a change-set for the given scanned directory.
     *
     * @param directory scanned directory
     * @param addedFiles files that were added (possibly empty)
     * @param modifiedFiles files that were modified (possibly empty)
     * @param removedFiles files that were removed (possibly empty)
     */
    public FileChangeSet(final File directory,
                         final Set<File> addedFiles,
                         final Set<File> modifiedFiles,
                         final Set<File> removedFiles) {
        this.directory = directory;
        this.addedFiles = addedFiles;
        this.modifiedFiles = modifiedFiles;
        this.removedFiles = removedFiles;
    }

    /**
     * Returns the scanned directory.
     * @return the scanned directory.
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Returns the (possibly empty) set of files that were added to the scanned directory.
     * The returned set is un-modifiable.
     * @return the (possibly empty) set of files that were added to the scanned directory.
     */
    public Set<File> getAddedFiles() {
        return unmodifiableSet(addedFiles);
    }

    /**
     * Returns the (possibly empty) set of files that were modified in the scanned directory.
     * The returned set is un-modifiable.
     * @return the (possibly empty) set of files that were modified in the scanned directory.
     */
    public Set<File> getModifiedFiles() {
        return unmodifiableSet(modifiedFiles);
    }

    /**
     * Returns the (possibly empty) set of files that were removed from the scanned directory.
     * The returned set is un-modifiable.
     * @return the (possibly empty) set of files that were removed from the scanned directory.
     */
    public Set<File> getRemovedFiles() {
        return unmodifiableSet(removedFiles);
    }
}
