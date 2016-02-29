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
package org.forgerock.http.filter.throttling;

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * A {@link DefaultRateThrottlingPolicy} is a delegating {@link ThrottlingPolicy} that ensures the returned
 * {@link ThrottlingRate} is never null. If the delegated {@link ThrottlingPolicy} returns {@code null}, then it
 * returns the specified default rate.
 */
public class DefaultRateThrottlingPolicy implements ThrottlingPolicy {

    private final ThrottlingRate defaultRate;
    private final ThrottlingPolicy delegate;

    /**
     * Constructs a new {@link DefaultRateThrottlingPolicy}.
     *
     * @param defaultRate the rate to return if the one returned by the delegate is null.
     * @param delegate the wrapped datasource to execute
     */
    public DefaultRateThrottlingPolicy(ThrottlingRate defaultRate, ThrottlingPolicy delegate) {
        this.defaultRate = checkNotNull(defaultRate, "The default rate can't be null");
        this.delegate = checkNotNull(delegate, "The delegate can't be null");
    }

    @Override
    public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
        return delegate.lookup(context, request)
                       .then(new Function<ThrottlingRate, ThrottlingRate, Exception>() {
                           @Override
                           public ThrottlingRate apply(ThrottlingRate rate) throws Exception {
                               return rate == null ? defaultRate : rate;
                           }
                       });
    }

}
