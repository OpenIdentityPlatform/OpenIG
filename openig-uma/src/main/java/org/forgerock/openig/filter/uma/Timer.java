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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.filter.uma;

import java.util.LinkedHashMap;
import java.util.Map;

//For figuring out whats taking so long
class Timer {

    private final Map<String, Long> times = new LinkedHashMap<String, Long>();

    void start(String key) {
        times.put(key, System.currentTimeMillis());
    }

    void stop(String key) {
        times.put(key, System.currentTimeMillis() - times.get(key));
    }

    Map<String, Long> getTimes() {
        return times;
    }

    public void clear() {
        times.clear();
    }
}
