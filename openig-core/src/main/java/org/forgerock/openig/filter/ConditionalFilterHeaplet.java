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

import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.filter.ConditionalFilter;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionRequestAsyncFunction;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Creates a {@link ConditionalFilter} into a {@link org.forgerock.openig.heap.Heap} environment.
 *
 * <pre>
 *     {@code {
 *         "type": "ConditionalFilter",
 *         "config": {
 *           "condition": "${not empty request.headers['foo']}",
 *           "delegate": "anotherFilter"
 *         }
 *       }
 *     }
 * </pre>
 */
public class ConditionalFilterHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        Filter delegate = config.get("delegate").as(requiredHeapObject(heap, Filter.class));

        JsonValue condition = config.get("condition").required();
        if (condition.isBoolean()) {
            if (condition.asBoolean()) {
                // Condition is always true: always executes the delegate filter
                return delegate;
            } else {
                // Condition is always false: directly executes the next handler, skipping the delegate
                return new Filter() {
                    @Override
                    public Promise<Response, NeverThrowsException> filter(Context context,
                                                                          Request request,
                                                                          Handler handler) {
                        return handler.handle(context, request);
                    }
                };
            }
        }

        final Expression<Boolean> expression = condition.expect(String.class)
                                                        .as(expression(Boolean.class, heap.getProperties()));
        return new ConditionalFilter(delegate, new ExpressionRequestAsyncFunction<>(expression));
    }
}

