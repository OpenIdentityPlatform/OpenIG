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

package org.forgerock.openig.decoration;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.heap.Heap;

/**
 * A decoration {@code Context} is a way to provide the decorator(s) all of the available
 * information about the instance to decorate.
 */
public interface Context {
    /**
     * Returns the name of the heap object being decorated.
     *
     * @return the name of the heap object being decorated.
     */
    String getName();

    /**
     * Returns the heap object being decorated configuration. Should be considered as a read-only view of the
     * configuration (does not trigger any reconfiguration).
     *
     * @return the heap object being decorated configuration.
     */
    JsonValue getConfig();

    /**
     * Returns the heap that spawned the decorated heap object. This permits decorators to borrow references just like
     * any component.
     *
     * @return the heap that spawned the decorated heap object.
     */
    Heap getHeap();
}
