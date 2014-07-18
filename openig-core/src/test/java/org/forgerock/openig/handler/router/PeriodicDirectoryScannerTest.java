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

import static org.mockito.Mockito.*;

import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PeriodicDirectoryScannerTest {

    @Mock
    private DirectoryScanner delegate;

    @Mock
    private TimeService time;

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
        PeriodicDirectoryScanner scanner = new PeriodicDirectoryScanner(delegate, time);
        scanner.setScanInterval(period);
    }

    @DataProvider
    public Object[][] intervalBelowThreshold() {
        // @Checkstyle:off
        return new Object[][] {
                { 0L },
                { 40L },
                { 99L }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "intervalBelowThreshold")
    public void testScannerInvocationIsFiltered(long interval) throws Exception {
        when(time.since(anyLong())).thenReturn(interval);

        PeriodicDirectoryScanner scanner = new PeriodicDirectoryScanner(delegate, time);
        scanner.setScanInterval(100);

        scanner.scan(listener);

        // Verify the delegate is not called
        verifyZeroInteractions(delegate);
    }

    @DataProvider
    public Object[][] intervalAboveOrEqualToThreshold() {
        // @Checkstyle:off
        return new Object[][] {
                { 100L },
                { 101L },
                { 400L }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "intervalAboveOrEqualToThreshold")
    public void testScannerInvocationIsPropagated(long interval) throws Exception {
        when(time.since(anyLong())).thenReturn(interval);

        PeriodicDirectoryScanner scanner = new PeriodicDirectoryScanner(delegate, time);
        scanner.setScanInterval(100);

        scanner.scan(listener);

        // Verify the delegate is called
        verify(delegate).scan(listener);
    }
}
