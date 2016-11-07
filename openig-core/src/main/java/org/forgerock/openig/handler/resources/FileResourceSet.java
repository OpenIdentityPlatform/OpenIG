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

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.forgerock.openig.handler.resources.MediaTypes.extensionOf;
import static org.forgerock.openig.handler.resources.MediaTypes.getMediaType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

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
    private final Path pathRoot;

    /**
     * Constructs a file-based {@link ResourceSet}, using the given {@code root} as root directory.
     * @param root root directory
     * @exception IOException In case an error occurred while getting the real path of the root directory.
     */
    public FileResourceSet(final File root) throws IOException {
        this.root = root;
        this.pathRoot = root.toPath().toRealPath(NOFOLLOW_LINKS);
    }

    @Override
    public Resource find(final String path) {
        File resource = new File(root, path);
        // Make sure we don't serve resources outside of the root directory
        if (resource.isFile()) {
            try {
                // The resource exists
                Path pathResource = resource.toPath().toRealPath(NOFOLLOW_LINKS);
                if (pathResource.startsWith(pathRoot)) {
                    return new FileResource(resource, getMediaType(extensionOf(path)));
                } else {
                    logger.warn("Blocked attempt to get resource outside of root directory: {}", path);
                }
            } catch (IOException e) {
                logger.error("Unable to get real paths of resource", e);
                return null;
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
