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


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;

public class FakeTimeService implements TimeService {

    private long current;
    private List<TimeServiceListener> listeners = new ArrayList<>();

    public FakeTimeService() {
        this(0);
    }

    public FakeTimeService(long start) {
        this.current = start;
    }

    @Override
    public long now() {
        return current;
    }

    @Override
    public long since(long past) {
        return now() - past;
    }

    public long advance(Duration duration) {
        return advance(duration.to(TimeUnit.MILLISECONDS));
    }

    public long advance(long delay, TimeUnit unit) {
        return advance(unit.toMillis(delay));
    }

    public long advance(long milliseconds) {
        this.current += milliseconds;
        for (TimeServiceListener listener : listeners) {
            listener.notifyCurrentTime(current);
        }
        return now();
    }

    public void registerTimeServiceListener(TimeServiceListener listener) {
        listeners.add(listener);
    }

    public interface TimeServiceListener {
        void notifyCurrentTime(long current);
    }
}

