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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Responses;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * A {@link EnforcerFilter} makes sure that the handled {@link Request} verifies a condition.
 * If the condition is not verified, it simply returns a {@literal 500} internal server error response
 * (that actually stops the chain execution).
 */
public class EnforcerFilter implements Filter {

    private final Expression<Boolean> enforcement;
    private final Filter delegate;
    private final Logger logger;

    /**
     * Creates a new {@link EnforcerFilter} delegating to the given {@link Filter} if the enforcement expression yields
     * {@literal true}.
     *
     * @param enforcement
     *         {@link Expression} that needs to evaluates to {@literal true} for the delegating Filter to be executed.
     * @param delegate
     *         Filter instance to delegate to.
     * @param logger
     *         For logging activities.
     */
    public EnforcerFilter(final Expression<Boolean> enforcement, final Filter delegate, final Logger logger) {
        this.enforcement = enforcement;
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        if (!isConditionVerified(bindings(context, request))) {
            logger.error(format("Cannot satisfy the enforcement expression '%s'", enforcement.toString()));
            return newResultPromise(Responses.newInternalServerError());
        }
        return delegate.filter(context, request, next);
    }

    private boolean isConditionVerified(final Bindings bindings) {
        return TRUE.equals(enforcement.eval(bindings));
    }
}
