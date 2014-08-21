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

import org.forgerock.http.io.TemporaryStorage;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;


/**
 * Creates and initializes a temporary storage object in a heap environment.
 */
public class TemporaryStorageHeaplet extends NestedHeaplet {

    /**
     * Key to retrieve a {@link TemporaryStorage} instance from the {@link org.forgerock.openig.heap.Heap}.
     */
    public static final String TEMPORARY_STORAGE_HEAP_KEY = "TemporaryStorage";

    @Override
    public Object create() throws HeapException {
        return new TemporaryStorage(
                config.get("directory").asFile(),
                config.get("initialLength").defaultTo(TemporaryStorage.HEIGHT_KB).asInteger(),
                config.get("memoryLimit").defaultTo(TemporaryStorage.SIXTY_FOUR_KB).asInteger(),
                config.get("fileLimit").defaultTo(TemporaryStorage.ONE_MB).asInteger());
    }
}
