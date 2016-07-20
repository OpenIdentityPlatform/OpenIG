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

package org.forgerock.openig.decoration;

import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * This heaplet aims to be be a placeholder so you can decorate the delegate object with any decorators.
 */
public class DelegateHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        return config.get("delegate").as(requiredHeapObject(heap, Object.class));
    }
}

