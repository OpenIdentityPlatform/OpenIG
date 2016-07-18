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

package org.forgerock.openig.util;

import static java.lang.String.format;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.util.Function;
import org.forgerock.util.annotations.VisibleForTesting;

@VisibleForTesting
class HeapObjectNameOrPointerJsonTransformFunction implements Function<JsonValue, String, JsonValueException> {

    @Override
    public String apply(JsonValue heapObjectRef) {
        // Ref to an already defined heap object
        if (heapObjectRef.isString()) {
            return heapObjectRef.asString();
        }
        // Inline declaration
        if (heapObjectRef.isMap()) {
            JsonValue name = heapObjectRef.get("name");
            if (name.isNotNull()) {
                return name.asString();
            }
            String location = heapObjectRef.getPointer().toString();
            String type = heapObjectRef.get("type").required().asString();
            return format("{%s}%s", type, location);
        }
        throw new JsonValueException(heapObjectRef, "Expecting a string or an object definition");
    }
}

