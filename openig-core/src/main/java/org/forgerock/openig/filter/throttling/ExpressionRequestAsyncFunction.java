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

package org.forgerock.openig.filter.throttling;

import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.openig.el.Expression;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;

/**
 * This is an implementation of the {@link AsyncFunction} based on the evaluation of an {@link Expression}.
 * @param <V>
 */
class ExpressionRequestAsyncFunction<V> implements AsyncFunction<ContextAndRequest, V, Exception> {

    private final Expression<V> expression;

    public ExpressionRequestAsyncFunction(Expression<V> expression) {
        this.expression = expression;
    }

    @Override
    public Promise<V, Exception> apply(ContextAndRequest contextAndRequest) {
        return newResultPromise(expression.eval(bindings(contextAndRequest.getContext(),
                                                         contextAndRequest.getRequest())));
    }
}
