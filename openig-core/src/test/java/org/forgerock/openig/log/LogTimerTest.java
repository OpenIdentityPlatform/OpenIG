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

package org.forgerock.openig.log;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.forgerock.openig.heap.Name;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LogTimerTest {

    @Spy
    private Logger logger = new Logger(null, Name.of("Test"));

    @Captor
    private ArgumentCaptor<LogEntry> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldComputeElapsedTime() throws Exception {
        LogTimer timer = new LogTimer(logger);
        timer.start();
        timer.stop();

        verify(logger, times(2)).log(captor.capture());

        LogEntry started = captor.getAllValues().get(0);
        assertThat(started.getType()).isEqualTo("started");
        assertThat(started.getMessage()).isNotNull()
                                        .isNotEmpty();

        LogEntry elapsed = captor.getAllValues().get(1);
        assertThat(elapsed.getType()).isEqualTo("elapsed");
        assertThat(elapsed.getMessage()).isNotNull()
                                        .isNotEmpty();
        assertThat(elapsed.getData()).isInstanceOf(LogMetric.class);
    }

    @Test
    public void shouldComputeElapsedTimeWithPauses() throws Exception {
        LogTimer timer = new LogTimer(logger);
        timer.start();
        Thread.sleep(5);
        timer.pause();
        Thread.sleep(5);
        timer.resume();
        Thread.sleep(5);
        timer.stop();

        verify(logger, times(3)).log(captor.capture());

        LogEntry started = captor.getAllValues().get(0);
        assertThat(started.getType()).isEqualTo("started");
        assertThat(started.getMessage()).isNotNull()
                                        .isNotEmpty();

        LogEntry elapsed = captor.getAllValues().get(1);
        assertThat(elapsed.getType()).isEqualTo("elapsed");
        assertThat(elapsed.getMessage()).isNotNull()
                                        .isNotEmpty();
        LogMetric elapsedData = (LogMetric) elapsed.getData();
        assertThat(elapsedData).isNotNull();

        LogEntry within = captor.getAllValues().get(2);
        assertThat(within.getType()).isEqualTo("elapsed-within");
        assertThat(within.getMessage()).isNotNull()
                                       .isNotEmpty();
        LogMetric withinData = (LogMetric) within.getData();
        assertThat(withinData).isNotNull();

        // Let cross our fingers and hope that this comparison will always be OK
        assertThat((Long) elapsedData.getValue())
                .isGreaterThan((Long) withinData.getValue());
    }
}
