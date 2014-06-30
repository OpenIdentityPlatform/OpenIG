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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.io;

import java.io.File;

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.util.Factory;


/**
 * Allocates temporary buffers for caching streamed content during request processing.
 */
public class TemporaryStorage implements Factory<Buffer> {

    /**
     * 8 KiB.
     */
    public static final int HEIGHT_KB = 8 * 1024;

    /**
     * 64 KiB.
     */
    public static final int SIXTY_FOUR_KB = 64 * 1024;

    /**
     * 1 MiB.
     */
    public static final int ONE_MB = 1 * 1024 * 1024;

    /**
     * The initial length of memory buffer byte array. Default: 8 KiB.
     */
    private final int initialLength;

    /**
     * The length limit of the memory buffer. Attempts to exceed this limit will result in
     * promoting the buffer from a memory to a file buffer. Default: 64 KiB.
     */
    private final int memoryLimit;

    /**
     * The length limit of the file buffer. Attempts to exceed this limit will result in an
     * {@link OverflowException} being thrown. Default: 1 MiB.
     */
    private final int fileLimit;

    /**
     * The directory where temporary files are created. If {@code null}, then the
     * system-dependent default temporary directory will be used. Default: {@code null}.
     *
     * @see java.io.File#createTempFile(String, String, File)
     */
    private final File directory;

    /**
     * Builds a storage using the system dependent default temporary directory and default sizes.
     * Equivalent to call {@code new TemporaryStorage(null)}.
     * @see #TemporaryStorage(File)
     */
    public TemporaryStorage() {
        this(null);
    }

    /**
     * Builds a storage using the given directory (may be {@literal null}) and default sizes. Equivalent to call {@code
     * new TemporaryStorage(directory, HEIGHT_KB, SIXTY_FOUR_KB, ONE_MB)}.
     *
     * @param directory
     *         The directory where temporary files are created. If {@code null}, then the system-dependent default
     *         temporary directory will be used.
     * @see #TemporaryStorage(File, int, int, int)
     */
    public TemporaryStorage(final File directory) {
        this(directory, HEIGHT_KB, SIXTY_FOUR_KB, ONE_MB);
    }

    /**
     * Builds a storage using the given directory (may be {@literal null}) and provided sizes.
     *
     * @param directory
     *         The directory where temporary files are created. If {@code null}, then the system-dependent default
     *         temporary directory will be used.
     * @param initialLength
     *         The initial length of memory buffer byte array.
     * @param memoryLimit
     *         The length limit of the memory buffer. Attempts to exceed this limit will result in promoting the buffer
     *         from a memory to a file buffer.
     * @param fileLimit
     *         The length limit of the file buffer. Attempts to exceed this limit will result in an {@link
     *         OverflowException} being thrown.
     * @see #TemporaryStorage(File, int, int, int)
     */
    public TemporaryStorage(final File directory,
                            final int initialLength,
                            final int memoryLimit,
                            final int fileLimit) {
        this.initialLength = initialLength;
        this.memoryLimit = memoryLimit;
        this.fileLimit = fileLimit;
        this.directory = directory;
    }

    /**
     * Creates and returns a new instance of a temporary buffer.
     *
     * @return a new instance of a temporary buffer.
     */
    public Buffer newInstance() {
        return new TemporaryBuffer(initialLength, memoryLimit, fileLimit, directory);
    }

    /**
     * Creates and initializes a temporary storage object in a heap environment.
     */
    public static class Heaplet extends NestedHeaplet {
        @Override
        public Object create() throws HeapException {
            return new TemporaryStorage(config.get("directory").asFile(),
                                        config.get("initialLength").defaultTo(HEIGHT_KB).asInteger(),
                                        config.get("memoryLimit").defaultTo(SIXTY_FOUR_KB).asInteger(),
                                        config.get("fileLimit").defaultTo(ONE_MB).asInteger());
        }
    }
}
