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

package org.forgerock.openig.heap.domain;

import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Heaplet;
import org.forgerock.openig.heap.HeapletFactory;

@SuppressWarnings("javadoc")
public class EditorHeapletFactory implements HeapletFactory {
    @Override
    public Heaplet newInstance(final Class<?> type) {
        return Editor.class.equals(type) ? new EditorHeaplet() : null;
    }

    public static class EditorHeaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            return new Editor();
        }
    }

}
