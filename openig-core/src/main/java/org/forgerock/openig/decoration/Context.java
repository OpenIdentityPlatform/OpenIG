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

import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.helper.LazyReference;
import org.forgerock.openig.heap.Heap;
import org.forgerock.openig.heap.Name;

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
    Name getName();

    /**
     * Returns the heap object being decorated configuration. Should be considered as a read-only view of the
     * configuration (does not trigger any reconfiguration).
     *
     * @return the heap object being decorated configuration.
     */
    JsonValue getConfig();

    /**
     * Returns the heap that spawned the decorated heap object. This permits decorators to borrow references just like
     * any component. Notice that this reference has to be used with caution because any attempt to resolve a reference
     * in a decorator that is globally declared for a heap will produce an infinite recursion leading to a
     * java.lang.StackOverflowError. In order to prevent that, decorator's implementer are encouraged to use a
     * {@link LazyReference} that will load the reference lazily (ideally just before it is really needed, but not
     * during the loading of the heap).
     *
     * @return the heap that spawned the decorated heap object.
     * @see LazyReference
     */
    Heap getHeap();
}
