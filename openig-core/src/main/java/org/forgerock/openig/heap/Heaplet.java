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
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.heap;

// JSON Fluent
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.util.Indexed;

/**
 * Creates and initializes an object that is stored in a {@link Heap}. A heaplet can retrieve
 * object(s) it depends on from the heap.
 *
 * @author Paul C. Bryan
 */
@SuppressWarnings("rawtypes")
public interface Heaplet extends Indexed<Class> {

    /**
     * Returns the class of object that the heaplet will create.
     */
    @Override
    Class<?> getKey();

    /**
     * Called to request the heaplet create an object.
     *
     * @param name the name of the object to be created.
     * @param config the heaplet's configuration object.
     * @param heap the heap where object dependencies can be retrieved.
     * @return the object created by the heaplet.
     * @throws HeapException if an exception occurred during creation of the object or any of its dependencies.
     * @throws JsonValueException if the heaplet (or one of its dependencies) has a malformed configuration object.
     */
    Object create(String name, JsonValue config, Heap heap) throws HeapException, JsonValueException;

    /**
     * Called to indicate that the object created by the heaplet is going to be dereferenced.
     * This gives the heaplet an opportunity to free any resources that are being held prior
     * to its dereference.
     */
    void destroy();
}
