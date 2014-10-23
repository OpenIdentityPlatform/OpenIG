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

package org.forgerock.openig.decoration.helper;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.HeapException;

/**
 * An AbstractHandlerAndFilterDecorator is the base implementation for decorators working only on {@link Filter} and
 * {@link Handler}.
 * <p>
 * Implementors just have to implement the dedicated {@link #decorateFilter(Filter, JsonValue, Context)} and {@link
 * #decorateHandler(Handler, JsonValue, Context)} for decorating Filter and Handler respectively.
 */
public abstract class AbstractHandlerAndFilterDecorator implements Decorator {

    @Override
    public boolean accepts(final Class<?> type) {
        return Filter.class.isAssignableFrom(type) || Handler.class.isAssignableFrom(type);
    }

    @Override
    public Object decorate(final Object delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        // Only intercept if needed
        if (delegate instanceof Handler) {
            return decorateHandler((Handler) delegate, decoratorConfig, context);
        } else if (delegate instanceof Filter) {
            return decorateFilter((Filter) delegate, decoratorConfig, context);
        }
        return delegate;
    }

    /**
     * Decorates the provided {@code delegate} {@link Filter} instance with the provided {@code decoratorConfig}
     * configuration.
     *
     * @param delegate
     *         Filter instance to be decorated
     * @param decoratorConfig
     *         the decorator configuration to apply
     * @param context
     *         contextual information of the decorated instance
     * @return a decorated filter instance (or original filter delegate)
     * @throws HeapException
     *         when decoration fails
     */
    protected abstract Filter decorateFilter(final Filter delegate,
                                             final JsonValue decoratorConfig,
                                             final Context context) throws HeapException;

    /**
     * Decorates the provided {@code delegate} {@link Handler} instance with the provided {@code decoratorConfig}
     * configuration.
     *
     * @param delegate
     *         Handler instance to be decorated
     * @param decoratorConfig
     *         the decorator configuration to apply
     * @param context
     *         contextual information of the decorated instance
     * @return a decorated handler instance (or original handler delegate)
     * @throws HeapException
     *         when decoration fails
     */
    protected abstract Handler decorateHandler(final Handler delegate,
                                               final JsonValue decoratorConfig,
                                               final Context context) throws HeapException;
}