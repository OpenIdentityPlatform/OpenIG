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

import static org.forgerock.util.promise.Promises.newResultPromise;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * An implementation of the {@link ThrottlingPolicy} that always returns the same throtlling rate.
 */
public class FixedRateThrottlingPolicy implements ThrottlingPolicy {

    private final Promise<ThrottlingRate, Exception> promiseRate;

    /**
     * Constructs a new throttling policy that always returns the same throttling rate.
     * @param rate the rate to return
     */
    public FixedRateThrottlingPolicy(ThrottlingRate rate) {
        this.promiseRate = newResultPromise(rate);
    }

    @Override
    public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
        return promiseRate;
    }
}
