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

package org.forgerock.openig.handler.resources;

import static org.forgerock.openig.handler.resources.MediaTypes.getMediaType;
import static org.forgerock.openig.handler.resources.MediaTypes.extensionOf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link FileResourceSet} is able to give access to file-based content
 * within the scope of the {@code root} directory.
 */
public class FileResourceSet implements ResourceSet {

    private final static Logger logger = LoggerFactory.getLogger(FileResourceSet.class);

    /**
     * Root directory.
     */
    private final File root;

    /**
     * Constructs a file-based {@link ResourceSet}, using the given {@code root} as root directory.
     * @param root root directory
     */
    public FileResourceSet(final File root) {
        this.root = root;
    }

    @Override
    public Resource find(final String path) {
        File canonical;
        File resource = new File(root, path);
        try {
            canonical = resource.getCanonicalFile();
        } catch (IOException e) {
            logger.warn("Can't get canonical path for file {}", resource);
            return null;
        }
        // Make sure we don't serve resources outside of the root directory
        if (canonical.isFile()) {
            // The resource exists
            if (canonical.getPath().startsWith(root.getPath())) {
                return new FileResource(canonical, getMediaType(extensionOf(path)));
            } else {
                logger.warn("Blocked attempt to get resource outside of root directory: {}", path);
            }
        }
        return null;
    }

    static class FileResource extends AbstractResource {

        private final File file;

        FileResource(final File file, final String type) {
            super(type);
            this.file = file;
        }

        @Override
        public InputStream open() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public long getLastModified() {
            return file.lastModified();
        }
    }
}
