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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.io;

import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.json.JsonValueFunctions.file;

import org.forgerock.http.io.IO;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.Function;

/**
 * A Heaplet to call {@link IO#newTemporaryStorage} within a heaplet environment.
 */
public class TemporaryStorageHeaplet extends GenericHeaplet {
    @Override
    public Object create() throws HeapException {
        JsonValue evaluated = config.as(evaluatedWithHeapProperties());
        return newTemporaryStorage(evaluated.get("directory").as(file()),
                                   evaluated.get("initialLength")
                                            .defaultTo(IO.DEFAULT_TMP_INIT_LENGTH)
                                            .as(positiveInteger()),
                                   evaluated.get("memoryLimit")
                                            .defaultTo(IO.DEFAULT_TMP_MEMORY_LIMIT)
                                            .as(positiveInteger()),
                                   evaluated.get("fileLimit")
                                            .defaultTo(IO.DEFAULT_TMP_FILE_LIMIT)
                                            .as(positiveInteger()));
    }

    private static Function<JsonValue, Integer, JsonValueException> positiveInteger() {
        return new Function<JsonValue, Integer, JsonValueException>() {
            @Override
            public Integer apply(JsonValue jsonValue) {
                Integer result = jsonValue.asInteger();
                if (result > 0) {
                    return result;
                }
                throw new JsonValueException(jsonValue, "Expected a positive integer.");
            }
        };
    }
}
