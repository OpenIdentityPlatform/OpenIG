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
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PeriodicDirectoryScannerTest {

    @Mock
    private DirectoryMonitor directoryMonitor;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private FileChangeListener listener;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @DataProvider
    public Object[][] invalidPeriods() {
        // @Checkstyle:off
        return new Object[][] {
                { 0 },
                { -1 },
                { -42 },
                { -8008 }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidPeriods",
          expectedExceptions = IllegalArgumentException.class)
    public void testInvalidPeriod(int period) throws Exception {
        PeriodicDirectoryScanner scanner = new PeriodicDirectoryScanner(directoryMonitor, null);
        scanner.setScanInterval(period);
    }

    @Test
    public void testScheduling() throws Exception {
        // The goal is not to test that the ScheduledExecutorService is launching at fixed intervals a scan,
        // but just to ensure that we use the ScheduledExecutorService correctly : i.e. scheduling the task
        // and cancelling it.
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        when(scheduledExecutorService.scheduleAtFixedRate(any(Runnable.class),
                                                          eq(0L),
                                                          eq(100L),
                                                          eq(TimeUnit.MILLISECONDS)))
                .thenReturn(scheduledFuture);

        PeriodicDirectoryScanner scanner = new PeriodicDirectoryScanner(directoryMonitor, scheduledExecutorService);
        scanner.setScanInterval(100);
        scanner.start();

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class),
                                                             eq(0L),
                                                             eq(100L),
                                                             eq(TimeUnit.MILLISECONDS));

        scanner.stop();
        verify(scheduledFuture).cancel(anyBoolean());
    }
}
