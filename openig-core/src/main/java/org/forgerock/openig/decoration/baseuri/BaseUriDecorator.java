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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static org.forgerock.openig.util.JsonValues.expression;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.Filters;
import org.forgerock.http.handler.Handlers;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.decoration.Context;
import org.forgerock.openig.decoration.Decorator;
import org.forgerock.openig.decoration.helper.AbstractHandlerAndFilterDecorator;
import org.forgerock.openig.decoration.helper.DecoratorHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * The {@literal baseURI} decorator can decorate both {@link Filter} and {@link Handler} instances.
 * <p>
 * It has to be declared inside of the heap objects section:
 * <pre>
 *     {@code
 *     {
 *       "name": "myBaseUri",
 *       "type": "BaseUriDecorator"
 *     }
 *     }
 * </pre>
 * To decorate a component, just add the decorator declaration next to the {@code config} element:
 * <pre>
 *     {@code
 *     {
 *       "type": "...",
 *       "myBaseUri": "http://www.example.com",
 *       "config": { ... }
 *     }
 *     }
 * </pre>
 * <p>
 * The {@literal baseURI} has to be a string otherwise, the decoration will be ignored.
 * <p>
 * N.B: The Gateway Servlet creates a default BaseUriDecorator named "baseURI" at startup time.
 */
public class BaseUriDecorator extends AbstractHandlerAndFilterDecorator {

    /**
     * Builds a new {@code BaseUriDecorator}.
     *
     * @param name
     *            The name of this decorator.
     */
    public BaseUriDecorator(final String name) {
        super(name);
    }

    @Override
    protected Filter decorateFilter(final Filter delegate, final JsonValue decoratorConfig, final Context context)
            throws HeapException {
        if (decoratorConfig.isString()) {
            return Filters.chainOf(createBaseUriFilter(decoratorConfig, context), delegate);
        }
        return delegate;
    }

    @Override
    protected Handler decorateHandler(final Handler delegate, final JsonValue decoratorConfig, final Context context) {
        if (decoratorConfig.isString()) {
            return Handlers.chainOf(delegate, createBaseUriFilter(decoratorConfig, context));
        }
        return delegate;
    }

    private BaseUriFilter createBaseUriFilter(final JsonValue decoratorConfig, final Context context) {
        return new BaseUriFilter(decoratorConfig.as(expression(String.class)),
                                 getLogger(context));
    }

    /** Creates and initializes a baseUri in a heap environment. */
    public static class Heaplet extends DecoratorHeaplet {
        @Override
        public Decorator create() throws HeapException {
            return new BaseUriDecorator(name.getLeaf());
        }
    }
}
