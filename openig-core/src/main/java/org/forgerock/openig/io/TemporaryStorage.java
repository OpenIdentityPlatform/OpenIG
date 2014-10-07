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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.io;

import org.forgerock.http.io.Buffer;
import org.forgerock.http.io.IO;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.Factory;

/**
 * A wrapper class around {@link IO#newTemporaryStorage} to make it usable
 * within a heaplet environment.
 */
public class TemporaryStorage implements Factory<Buffer> {
    /**
     * Key to retrieve a {@link TemporaryStorage} instance from the
     * {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TEMPORARY_STORAGE_HEAP_KEY = "TemporaryStorage";

    private final Factory<Buffer> factory;

    /**
     * Creates a new temporary storage with a default implementation.
     */
    public TemporaryStorage() {
        this(IO.newTemporaryStorage());
    }

    private TemporaryStorage(final Factory<Buffer> factory) {
        this.factory = factory;
    }

    @Override
    public Buffer newInstance() {
        return factory.newInstance();
    }

    /**
     * Creates and initializes a temporary storage object in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {
            return new TemporaryStorage(IO.newTemporaryStorage(config.get("directory").asFile(),
                    config.get("initialLength").defaultTo(IO.DEFAULT_TMP_INIT_LENGTH).asInteger(),
                    config.get("memoryLimit").defaultTo(IO.DEFAULT_TMP_MEMORY_LIMIT).asInteger(),
                    config.get("fileLimit").defaultTo(IO.DEFAULT_TMP_FILE_LIMIT).asInteger()));
        }
    }
}
