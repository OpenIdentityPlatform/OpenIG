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

package org.forgerock.openig.decoration.global;

import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.heap.HeapException;

/**
 * A GlobalDecorator stores decorators configuration in order to re-apply them when requested
 * to decorate a given heap object instance.
 */
public class GlobalDecorator implements Decorator {

    /**
     * Heap Key for the global decorator(s). They may be local to each Heap.
     */
    public static final String GLOBAL_DECORATOR_HEAP_KEY = "global-decorator";

    private final JsonValue decorators;

    /**
     * Builds a new GlobalDecorator using given decorators JSON object element.
     *
     * @param decorators
     *         a JSON object structure
     */
    public GlobalDecorator(final JsonValue decorators) {
        this.decorators = decorators.expect(Map.class);
    }

    @Override
    public boolean accepts(final Class<?> type) {
        // Not used
        return true;
    }

    /**
     * Decorate the given object instance with the previously declared set of decorations instead of the provided one.
     *
     * @param delegate
     *         instance to decorate
     * @param ignored
     *         ignored (may probably be {@code null})
     * @param context
     *         Context of the heap object to be decorated
     * @return the decorated instance or the original delegate (if no decorator could apply)
     * @throws HeapException
     *         if one of the decorator failed to decorate the instance
     */
    @Override
    public Object decorate(final Object delegate, final JsonValue ignored, final Context context)
            throws HeapException {
        Object decorated = delegate;
        for (JsonValue decoration : decorators) {
            String decoratorName = decoration.getPointer().leaf();
            // Process the decoration
            Decorator decorator = context.getHeap().get(decoratorName, Decorator.class);

            if ((decorator != null) && decorator.accepts(delegate.getClass())) {
                decorated = decorator.decorate(decorated, decoration, context);
            }
        }
        return decorated;
    }
}
