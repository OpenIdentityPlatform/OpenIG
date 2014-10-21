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
import org.forgerock.openig.heap.HeapException;

/**
 * A Decorator is responsible for decorating existing object's instances.
 * <p>
 * A Decorator cannot change the base type of the provided delegate: for example if the given instance is
 * a {@code Filter}, the decorated (and returned) instance must also be a {@code Filter}, sub-classing is ok though.
 * <p>
 * <b>Notice:</b> This API is still considered experimental and is subject to change in subsequent releases.
 */
public interface Decorator {

    /**
     * Returns {@code true} if this decorator is compatible with the provided component type. Note that a return value
     * of {@code true} does not necessarily indicate that decoration will be performed since it may also depend on other
     * factors
     *
     * @param type
     *         type under test
     * @return {@code true} if the decorator can decorate instance of the given type, {@code false} otherwise.
     */
    boolean accepts(Class<?> type);

    /**
     * Decorates the provided {@code delegate} instance with the provided {@code decoratorConfig} configuration.
     * The implementation should take care of not changing the base type of the delegate.
     *
     * @param delegate
     *         instance to be decorated
     * @param decoratorConfig
     *         the decorator configuration to apply
     * @param context
     *         contextual information of the decorated instance
     * @return a decorated instance (or original delegate)
     * @throws HeapException when decoration fails
     */
    Object decorate(Object delegate, JsonValue decoratorConfig, Context context) throws HeapException;
}
