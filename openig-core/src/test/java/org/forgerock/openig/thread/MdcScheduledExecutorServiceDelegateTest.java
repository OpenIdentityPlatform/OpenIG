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

package org.forgerock.openig.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MdcScheduledExecutorServiceDelegateTest extends MdcExecutorServiceDelegateTest {

    @Mock
    private ScheduledExecutorService delegate;

    @Mock
    private Runnable runnable;

    @Mock
    private Callable<String> callable;

    @Mock
    private ScheduledFuture<String> scheduledFuture;

    @Captor
    private ArgumentCaptor<Runnable> runnableCapture;

    @Captor
    private ArgumentCaptor<Callable<String>> callableCapture;

    @Captor
    private ArgumentCaptor<Collection<Callable<String>>> callablesCapture;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Override
    protected ScheduledExecutorService executorService() {
        return new MdcScheduledExecutorServiceDelegate(delegate());
    }

    protected ScheduledExecutorService delegate() {
        return delegate;
    }


    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailBecauseNoDelegateIsProvided() throws Exception {
        new MdcScheduledExecutorServiceDelegate(null);
    }

    @Test
    public void shouldScheduleWithRunnableLongTimeUnit() throws Exception {
        doReturn(scheduledFuture).when(delegate())
                                 .schedule(nullable(Runnable.class), eq(1L), eq(TimeUnit.SECONDS));

        assertThat(executorService().schedule(runnable, 1, TimeUnit.SECONDS)).isSameAs(scheduledFuture);

        verify(delegate()).schedule(runnableCapture.capture(), eq(1L), eq(TimeUnit.SECONDS));
        assertThatCapturedRunnableIsMdcAware();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldScheduleWithCallableLongTimeUnit() throws Exception {
        doReturn(scheduledFuture).when(delegate())
                                 .schedule(nullable(Callable.class), eq(1L), eq(TimeUnit.SECONDS));

        assertThat(executorService().schedule(callable, 1, TimeUnit.SECONDS)).isSameAs(scheduledFuture);

        verify(delegate()).schedule(callableCapture.capture(), eq(1L), eq(TimeUnit.SECONDS));
        assertThatCaptureCallableIsMdcAware();
    }

    @Test
    public void shouldScheduleAtFixedRateWithRunnableLongTimeUnit() throws Exception {
        doReturn(scheduledFuture).when(delegate())
                                 .scheduleAtFixedRate(nullable(Runnable.class), eq(1L), eq(2L), eq(TimeUnit.SECONDS));

        assertThat(executorService().scheduleAtFixedRate(runnable, 1, 2, TimeUnit.SECONDS)).isSameAs(scheduledFuture);

        verify(delegate()).scheduleAtFixedRate(runnableCapture.capture(), eq(1L), eq(2L), eq(TimeUnit.SECONDS));
        assertThatCapturedRunnableIsMdcAware();
    }

    @Test
    public void shouldScheduleWithFixedDelayWithRunnableLongTimeUnit() throws Exception {
        doReturn(scheduledFuture).when(delegate())
                                 .scheduleWithFixedDelay(nullable(Runnable.class), eq(1L), eq(2L), eq(TimeUnit.SECONDS));

        assertThat(executorService().scheduleWithFixedDelay(runnable, 1, 2, TimeUnit.SECONDS))
                .isSameAs(scheduledFuture);

        verify(delegate()).scheduleWithFixedDelay(runnableCapture.capture(), eq(1L), eq(2L), eq(TimeUnit.SECONDS));
        assertThatCapturedRunnableIsMdcAware();
    }

    private void assertThatCapturedRunnableIsMdcAware() {
        assertThat(runnableCapture.getValue())
                .isInstanceOf(MdcScheduledExecutorServiceDelegate.MdcRunnable.class);
    }

    private void assertThatCaptureCallableIsMdcAware() {
        assertThat(callableCapture.getValue())
                .isInstanceOf(MdcScheduledExecutorServiceDelegate.MdcCallable.class);
    }
}
