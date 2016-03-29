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
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.util.Map;

import org.forgerock.http.ContextAndRequest;
import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * Implementation of {@link ThrottlingPolicy} backed by a {@link Map}.
 *
 * <ul>
 *     <li>if the key is null, then the {@code defaultRate} is returned</li>
 *     <li>if the key can be looked up as key in the {@code throttlingRatesMapping}, then the matching rate is returned
 *     </li>
 *     <li>if the key can't be looked up in the {@code throttlingRatesMapping}, then the {@code defaultRate} is returned
 *     </li>
 * </ul>
 *
 */
public class MappedThrottlingPolicy implements ThrottlingPolicy {

    private final Map<String, ThrottlingRate> rates;
    private final ThrottlingRate defaultRate;
    private final AsyncFunction<ContextAndRequest, String, Exception> throttlingRateMapper;

    /**
     * Constructs a new {@link MappedThrottlingPolicy}.
     * @param throttlingRateMapper the key to lookup the throttling rate
     * @param throttlingRatesMapping the Map to look into to find the matching throttling rate.
     * @param defaultRate the default throttling definition.
     */
    public MappedThrottlingPolicy(AsyncFunction<ContextAndRequest, String, Exception> throttlingRateMapper,
                                  Map<String, ThrottlingRate> throttlingRatesMapping,
                                  ThrottlingRate defaultRate) {
        this.throttlingRateMapper = checkNotNull(throttlingRateMapper);
        this.rates = checkNotNull(throttlingRatesMapping);
        this.defaultRate = defaultRate;
    }

    @Override
    public Promise<ThrottlingRate, Exception> lookup(Context context, Request request) {
        return newResultPromise(new ContextAndRequest(context, request))
                       .thenAsync(throttlingRateMapper)
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
