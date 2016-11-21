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

import static org.forgerock.openig.filter.throttling.ThrottlingFilterHeaplet.throttlingRate;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import org.forgerock.http.filter.throttling.DefaultRateThrottlingPolicy;
import org.forgerock.http.filter.throttling.ThrottlingPolicy;
import org.forgerock.http.filter.throttling.ThrottlingRate;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;

/**
 * Creates and initializes a {@link DefaultRateThrottlingPolicy} in a heap environment.
 * Configuration options:
 *
 * <pre>
 * {@code
 * {
 *     "type": "DefaultRateThrottlingPolicy",
 *     "config": {
 *         "delegateThrottlingRatePolicy"  : reference or               [REQUIRED - the policy that will define the
 *                                           inlined declaration                    throttling rate to apply]
 *         "defaultRate" {                 : reference                  [OPTIONAL - the default rate to apply if
 *                                                                                  the delegated throttling rate
 *                                                                                  policy returns no rate to apply]
 *                "numberOfRequests"             : integer              [REQUIRED - The number of requests allowed
 *                                                                                  to go through this filter during
 *                                                                                  the duration window.]
 *                "duration"                     : duration             [REQUIRED - The time window during which the
 *                                                                                  incoming requests are counted.]
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * Example : it applies a rate of 15 requests / sec if the scripted throttling rate policy returns no rate.
 * <pre>
 * {@code
 * {
 *     "type": "DefaultRateThrottlingPolicy",
 *     "config": {
 *         "delegateThrottlingRatePolicy" : "customThrottlingRatePolicy"
 *         "defaultRate" : {
 *             "numberOfRequests" : 15,
 *             "duration" : "1 sec"
 *         }
 *     }
 * }
 * }
 * </pre>
 */
public class DefaultRateThrottlingPolicyHeaplet extends GenericHeaplet {

    @Override
    public Object create() throws HeapException {
        ThrottlingPolicy throttlingPolicy = config.get("delegateThrottlingRatePolicy")
                                                  .as(requiredHeapObject(heap, ThrottlingPolicy.class));
        ThrottlingRate defaultRate = config.get("defaultRate").required().as(throttlingRate(heap.getProperties()));

        return new DefaultRateThrottlingPolicy(defaultRate, throttlingPolicy);
    }
}
