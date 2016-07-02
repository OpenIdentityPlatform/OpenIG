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

package org.forgerock.openig.filter;

import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.util.List;

import org.forgerock.http.Filter;
import org.forgerock.http.filter.Filters;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Allow to build a chain of filters as one filter.
 */
public class ChainFilterHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        List<Filter> filters = config.get("filters")
                                     .required()
                                     .expect(List.class)
                                     .as(listOf(requiredHeapObject(heap, Filter.class)));
        return Filters.chainOf(filters);
    }
}
