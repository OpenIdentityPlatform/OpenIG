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

import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This interface defines the contract for any throttling strategy.
 */
public interface ThrottlingStrategy {

    /**
     * Based on the given {@literal partitionKey} and {@literal throttlingRate}, return if the call is accepted or not.
     * The returned promise is succeeded with a value of 0 if the call is accepted. Any value greater than 0 means that
     * the next call that can be accepted after {@literal value} seconds.
     * This method returns a promise as the decision to let this call going through can be queued and thus completed
     * later. That may allow for example to queue the first calls and complete the returned promises at a constant rate.
     *
     * @param partitionKey the key used to identify the different groups
     * @param throttlingRate the throttling rate to apply
     * @return a {@link Promise} meaning that the call is accepted (succeeds with 0) or refused (succeeds with value
     * greater than 0)
     */
    Promise<Long, NeverThrowsException> throttle(String partitionKey, ThrottlingRate throttlingRate);

    /**
     * Stop and free any resources needed by the strategy.
     */
    void stop();

}
