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

import static java.lang.Boolean.TRUE;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ConditionEnforcementFilter} makes sure that the handled {@link Request} verifies
 * a condition. If the condition is verified, the chain of execution continues.
 * If the condition is not verified, it returns a {@literal 403} Forbidden response by default,
 * that actually stops the chain or could refers to a failure handler.
 * <p>
 * Configuration options:
 *
 * <pre>
 * {@code
 * {
 *     "condition"              : expression,         [REQUIRED]
 *     "failureHandler          : handler             [OPTIONAL - default to 403]
 * }
 * }
 *
 * </pre>
 *
 * For example:
 *
 * <pre>
 * {@code
 * {
 *     "type": "ConditionEnforcementFilter",
 *     "config": {
 *         "condition"             : "${not empty(attributes.myAttribute)}",
 *         "failureHandler"        : "ConditionFailed"
 *     }
 * }
 * }
 * </pre>
 */
public class ConditionEnforcementFilter extends GenericHeapObject implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ConditionEnforcementFilter.class);

    private final Expression<Boolean> condition;
    private final Handler failureHandler;

    /**
     * Creates a new {@link ConditionEnforcementFilter}. If the condition fails,
     * default {@link Handlers#FORBIDDEN} will be used.
     *
     * @param condition
     *            {@link Expression} that needs to evaluates to {@literal true}
     *            to continue the chain of execution.
     */
    public ConditionEnforcementFilter(final Expression<Boolean> condition) {
        this(condition, Handlers.FORBIDDEN);
    }

    /**
     * Creates a new {@link ConditionEnforcementFilter}.
     *
     * @param condition
     *            {@link Expression} that needs to evaluates to {@literal true}
     *            to continue the chain of execution.
     * @param failureHandler
     *            The handler which will be invoked when condition fails.
     */
    public ConditionEnforcementFilter(final Expression<Boolean> condition, final Handler failureHandler) {
        this.condition = checkNotNull(condition);
        this.failureHandler = checkNotNull(failureHandler);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        if (!isConditionVerified(bindings(context, request))) {
            logger.debug("Cannot satisfy the enforcement's condition expression '{}'", condition);
            return failureHandler.handle(context, request);
        }
        return next.handle(context, request);
    }

    private boolean isConditionVerified(final Bindings bindings) {
        return TRUE.equals(condition.eval(bindings));
    }

    /** Creates and initializes an ConditionEnforcementFilter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {

            final Expression<Boolean> condition = config.get("condition").required().as(expression(Boolean.class));
            if (config.isDefined("failureHandler")) {
                return new ConditionEnforcementFilter(condition,
                                                      config.get("failureHandler")
                                                            .as(requiredHeapObject(heap, Handler.class)));
            } else {
                return new ConditionEnforcementFilter(condition);
            }
        }
    }
}
