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

import java.util.Map;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Implementation of {@link ThrottlingPolicy} backed by a {@link Map}.
 *
 * <ul>
 *     <li>if the key is null, then the {@code defaultRate} is returned</li>
 *     <li>if the key can be looked up as key in the {@code lookupMap}, then the matching rate is returned
 *     </li>
 *     <li>if the key can't be looked up in the {@code lookupMap}, then the {@code defaultRate} is returned
 *     </li>
 * </ul>
 *
 */
public class MappedThrottlingPolicy implements ThrottlingPolicy {

    private final Map<String, ThrottlingRate> rates;
    private final ThrottlingRate defaultRate;
    private final AsyncFunction<ContextAndRequest, String, Exception> throttlingRateKey;

    /**
     * Constructs a new {@link MappedThrottlingPolicy}.
     * @param throttlingRateKey the key to lookup the throttling rate
     * @param rates the Map to look into to find the throttling rates.
     * @param defaultRate the default throttling definition.
     */
    public MappedThrottlingPolicy(AsyncFunction<ContextAndRequest, String, Exception> throttlingRateKey,
                                  Map<String, ThrottlingRate> rates,
                                  ThrottlingRate defaultRate) {
        this.throttlingRateKey = checkNotNull(throttlingRateKey);
        this.rates = checkNotNull(rates);
        this.defaultRate = defaultRate;
    }

    @Override
    public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
        return Promises.newResultPromise(new ContextAndRequest(context, request))
                       .thenAsync(throttlingRateKey)
                       .then(new Function<String, ThrottlingRate, Exception>() {
                           @Override
                           public ThrottlingRate apply(String key) {
                               if (key == null) {
                                   return defaultRate;
                               }

                               ThrottlingRate rate = rates.get(key);
                               return rate != null ? rate : defaultRate;
                           }
                       });
    }

}
