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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

/**
 * Provides time related methods for computing elapsed durations.
 *
 * @since 2.2
 */
interface TimeService {

    /**
     * Returns a value that represents "now" since the epoch.
     * @return a value that represents "now" since the epoch.
     */
    long now();

    /**
     * Computes the elapsed time between {@linkplain #now() now} and {@code past}.
     * @param past time value to compare to now.
     * @return the elapsed time
     */
    long since(long past);
}
