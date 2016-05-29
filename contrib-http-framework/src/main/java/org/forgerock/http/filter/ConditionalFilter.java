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

package org.forgerock.http.filter;

import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter conditionally executes a delegate Filter given the result of a 'condition' function. If the condition is
 * true then the delegated filter is executed, skipped otherwise.
 */
public class ConditionalFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ConditionalFilter.class);

    private final Filter delegate;
    private final AsyncFunction<ContextAndRequest, Boolean, Exception> condition;

    /**
     * Constructs a {@link ConditionalFilter}.
     * <p>
     * This constructor is provided as an ease to write some code : since you have access to the boolean,
     * you may optimise the code like this :
     * <pre>
     *     {@code
     * if (condition) {
     *    return delegate;
     * } else {
     *    return new Filter() {
     *       @Override
     *       public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler handler) {
     *          return handler.handle(context, request);
     *       }
     *    };
     * }
     *     }
     * </pre>
     *
     * @param delegate
     *         the filter that will be executed if the condition is true.
     * @param condition
     *         the condition that controls the delegate filter's execution
     */
    public ConditionalFilter(final Filter delegate, final boolean condition) {
        this(delegate, new AsyncFunction<ContextAndRequest, Boolean, Exception>() {

            final Promise<Boolean, Exception> resultPromise = newResultPromise(condition);

            @Override
            public Promise<? extends Boolean, ? extends Exception> apply(ContextAndRequest contextAndRequest) {
                return resultPromise;
            }
        });
    }

    /**
     * Constructs a {@link ConditionalFilter}.
     *
     * @param delegate
     *         the filter that will be executed if the condition is true.
     * @param condition
     *         the function that will be executed at each request and will allow or not to execute the delegate filter.
     */
    public ConditionalFilter(Filter delegate,
                             AsyncFunction<ContextAndRequest, Boolean, Exception> condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        return evaluateCondition(context, request)
                .thenAsync(new AsyncFunction<Boolean, Response, NeverThrowsException>() {
                    @Override
                    public Promise<? extends Response, ? extends NeverThrowsException> apply(Boolean condition) {
                        if (condition) {
                            return delegate.filter(context, request, next);
                        }
                        return next.handle(context, request);
                    }
                });
    }

    private Promise<Boolean, NeverThrowsException> evaluateCondition(Context context, Request request) {
        // @Checkstyle:off
        return newResultPromise(new ContextAndRequest(context, request))
                .thenAsync(condition)
                .then(new Function<Boolean, Boolean, NeverThrowsException>() {
                          @Override
                          public Boolean apply(Boolean value) {
                              return value;
                          }
                      },
                      new Function<Exception, Boolean, NeverThrowsException>() {
                          @Override
                          public Boolean apply(Exception e) {
                              logger.error("An exception was caught, returning false", e);
                              return false;
                          }
                      });
        // @Checkstyle:on
    }
}

