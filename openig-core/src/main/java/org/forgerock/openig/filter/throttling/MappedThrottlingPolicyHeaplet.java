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

import static java.lang.String.format;
import static org.forgerock.openig.filter.throttling.ThrottlingFilterHeaplet.createThrottlingRate;
import static org.forgerock.openig.util.JsonValues.asExpression;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.filter.throttling.MappedThrottlingPolicy;
import org.forgerock.http.filter.throttling.ThrottlingRate;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Creates and initializes a {@link MappedThrottlingPolicy} in a heap environment.
 *
 * Configuration options:
 *
 * <pre>
 * {@code
 * {
 *     "type": "MappedThrottlingPolicy",
 *     "config": {
 *         "throttlingRateMapper"         : expression<String>              [REQUIRED - The expression that will be
 *                                                                                      evaluated to lookup for a
 *                                                                                      throttling rate.]
 *         "throttlingRatesMapping"       : {                               [REQUIRED - This is a in memory map used to
 *                                                                                      lookup the throttling rate to
 *                                                                                      apply.]
 *             "<throttlingRateKey>" {   : String                           [REQUIRED - The value to match the
 *                                                                                      result of the expression
 *                                                                                      throttlingRateMapper.]
 *                 "numberOfRequests"             : integer                 [REQUIRED - The number of requests allowed
 *                                                                                      to go through this filter during
 *                                                                                      the duration window.]
 *                 "duration"                     : duration                [REQUIRED - The time window during which the
 *                                                                                      incoming requests are counted.]
 *             }
 *         },
 *         "defaultRate" {                 : reference                      [OPTIONAL - the default rate to apply if
 *                                                                                      there is no match]
 *             "numberOfRequests"             : integer                     [REQUIRED - The number of requests allowed
 *                                                                                      to go through this filter during
 *                                                                                      the duration window.]
 *             "duration"                     : duration                    [REQUIRED - The time window during which the
 *                                                                                      incoming requests are counted.]
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * Example : apply different throttling rates depending of the header 'Origin'. If the header is not specified, let's
 * apply a default rate of 15 requests per second.
 * {@code
 * {
 *     "type": "MappedThrottlingPolicy",
 *     "config": {
 *         "throttlingRateMapper" : "${request.headers['Origin'][0]}"
 *         "throttlingRateMapping"  : {
 *             "http://www.alice.com" : {
 *                 "numberOfRequests" : 30,
 *                 "duration" : "1 hour"
 *             },
 *             "http://www.bob.com" : {
 *                 "numberOfRequests" : 42,
 *                 "duration" : "1 minute"
 *             }
 *         },
 *         "defaultRate" : {
 *             "numberOfRequests" : 15,
 *             "duration" : "1 sec"
 *         }
 *     }
 * }
 * }
 */
public class MappedThrottlingPolicyHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        Expression<String> throttlingRateMapper = asExpression(config.get("throttlingRateMapper").required(),
                                                               String.class);

        Map<String, ThrottlingRate> rates = new HashMap<>();
        JsonValue map = config.get("throttlingRatesMapping");
        for (String key : map.keys()) {
            try {
                rates.put(Expression.valueOf(key, String.class).eval(), createThrottlingRate(map.get(key)));
            } catch (ExpressionException e) {
                throw new HeapException(new JsonValueException(map, "Expecting a valid string expression", e));
            }
        }

        ThrottlingRate defaultRate = null;
        if (config.isDefined("defaultRate")) {
            defaultRate = createThrottlingRate(config.get("defaultRate"));
        }

        if (rates.isEmpty() && defaultRate == null) {
            logger.warning(format("No throttling rates defined for %s", name));
        }

        return new MappedThrottlingPolicy(new ExpressionRequestAsyncFunction<>(throttlingRateMapper),
                                          rates,
                                          defaultRate);
    }

}
