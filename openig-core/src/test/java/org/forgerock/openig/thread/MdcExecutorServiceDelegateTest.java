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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class MdcExecutorServiceDelegateTest {

    @Mock
    private ExecutorService delegate;

    @Mock
    private Runnable runnable;

    @Mock
    private Callable<String> callable;

    @Mock
    private Future<String> future;

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

    protected ExecutorService executorService() {
        return new MdcExecutorServiceDelegate(delegate());
    }

    protected ExecutorService delegate() {
        return delegate;
    }

    @BeforeMethod
    public void setUpMdc() throws Exception {
        MDC.put("key", "value");
    }

    @AfterMethod
    public void tearDownMdc() throws Exception {
        MDC.clear();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailBecauseNoDelegateIsProvided() throws Exception {
        new MdcExecutorServiceDelegate(null);
    }

    @Test
    public void shouldShutdown() throws Exception {
        executorService().shutdown();
        verify(delegate()).shutdown();
    }

    @Test
    public void shouldShutdownNow() throws Exception {
        executorService().shutdownNow();
        verify(delegate()).shutdownNow();
    }

    @Test
    public void shouldIsShutdown() throws Exception {
        executorService().isShutdown();
        verify(delegate()).isShutdown();
    }

    @Test
    public void shouldIsTerminated() throws Exception {
        executorService().isTerminated();
        verify(delegate()).isTerminated();
    }

    @Test
    public void shouldAwaitTermination() throws Exception {
        executorService().awaitTermination(1, TimeUnit.SECONDS);
        verify(delegate()).awaitTermination(1L, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSubmitCallable() throws Exception {
        doReturn(future).when(delegate())
                        .submit(any(Callable.class));

        assertThat(executorService().submit(callable)).isSameAs(future);

        verify(delegate()).submit(callableCapture.capture());
        assertThatCaptureCallableIsMdcAware();
    }

    @Test
    public void shouldSubmitRunnableAndT() throws Exception {
        doReturn(future).when(delegate())
                        .submit(any(Runnable.class), eq("Hello"));

        assertThat(executorService().submit(runnable, "Hello")).isSameAs(future);

        verify(delegate()).submit(runnableCapture.capture(), eq("Hello"));
        assertThatCapturedRunnableIsMdcAware();
    }

    @Test
    public void shouldSubmitRunnable() throws Exception {
        doReturn(future).when(delegate())
                        .submit(any(Runnable.class));

        assertThat(executorService().submit(runnable)).isSameAs(future);

        verify(delegate()).submit(runnableCapture.capture());
        assertThatCapturedRunnableIsMdcAware();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInvokeAllCollection() throws Exception {
        doReturn(singletonList(future)).when(delegate())
                                       .invokeAll(anyCollection());

        assertThat(executorService().invokeAll(singleton(callable)))
                .contains(future, atIndex(0))
                .hasSize(1);

        verify(delegate()).invokeAll(callablesCapture.capture());
        assertThatCapturedCallablesAreMdcAware();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInvokeAllCollectionLongTimeUnit() throws Exception {
        doReturn(singletonList(future)).when(delegate())
                                       .invokeAll(anyCollection(), eq(1L), eq(TimeUnit.SECONDS));

        assertThat(executorService().invokeAll(singleton(callable), 1, TimeUnit.SECONDS))
                .contains(future, atIndex(0))
                .hasSize(1);

        verify(delegate()).invokeAll(callablesCapture.capture(), eq(1L), eq(TimeUnit.SECONDS));
        assertThatCapturedCallablesAreMdcAware();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInvokeAnyCollection() throws Exception {
        doReturn("Hello").when(delegate())
                         .invokeAny(anyCollection());

        assertThat(executorService().invokeAny(singleton(callable))).isEqualTo("Hello");

        verify(delegate()).invokeAny(callablesCapture.capture());
        assertThatCapturedCallablesAreMdcAware();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldInvokeAnyCollectionLongTimeUnit() throws Exception {
        doReturn("Hello").when(delegate())
                         .invokeAny(anyCollection(), eq(1L), eq(TimeUnit.SECONDS));

        assertThat(executorService().invokeAny(singleton(callable), 1, TimeUnit.SECONDS)).isEqualTo("Hello");

        verify(delegate()).invokeAny(callablesCapture.capture(), eq(1L), eq(TimeUnit.SECONDS));
        assertThatCapturedCallablesAreMdcAware();
    }

    @Test
    public void shouldExecuteRunnable() throws Exception {
        executorService().execute(runnable);
        verify(delegate()).execute(runnableCapture.capture());
        assertThatCapturedRunnableIsMdcAware();
    }

    private void assertThatCapturedRunnableIsMdcAware() {
        assertThat(runnableCapture.getValue())
                .isInstanceOf(MdcScheduledExecutorServiceDelegate.MdcRunnable.class);
    }

    private void assertThatCapturedCallablesAreMdcAware() {
        assertThat(callablesCapture.getValue().iterator().next())
                .isInstanceOf(MdcScheduledExecutorServiceDelegate.MdcCallable.class);
    }

    private void assertThatCaptureCallableIsMdcAware() {
        assertThat(callableCapture.getValue())
                .isInstanceOf(MdcScheduledExecutorServiceDelegate.MdcCallable.class);
    }

    @Test
    public void shouldSetTheAppropriateMdcInRunnableWithPreviousMdcInstalled() throws Exception {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                assertThat(MDC.get("a")).isEqualTo("b");
                assertThat(MDC.get("key")).isNull();
                return null;
            }
        }).when(runnable).run();

        Map<String, String> newMdc = singletonMap("a", "b");
        Runnable decorated = new MdcScheduledExecutorServiceDelegate.MdcRunnable(runnable, newMdc);

        decorated.run();

        assertThat(MDC.get("a")).isNull();
        assertThat(MDC.get("key")).isEqualTo("value");
        verify(runnable).run();
    }

    @Test
    public void shouldSetTheAppropriateMdcInRunnableWithNoPreviousMdcInstalled() throws Exception {
        // Clear any previous installed MDC
        MDC.clear();

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                assertThat(MDC.get("a")).isEqualTo("b");
                return null;
            }
        }).when(runnable).run();

        Map<String, String> newMdc = singletonMap("a", "b");
        Runnable decorated = new MdcScheduledExecutorServiceDelegate.MdcRunnable(runnable, newMdc);

        decorated.run();

        assertThat(MDC.getCopyOfContextMap()).isNull();
        verify(runnable).run();
    }

    @Test
    public void shouldSetTheAppropriateMdcInCallableWithPreviousMdcInstalled() throws Exception {
        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                assertThat(MDC.get("a")).isEqualTo("b");
                assertThat(MDC.get("key")).isNull();
                return null;
            }
        }).when(callable).call();

        Map<String, String> newMdc = singletonMap("a", "b");
        Callable<String> decorated = new MdcScheduledExecutorServiceDelegate.MdcCallable<>(callable, newMdc);

        decorated.call();

        assertThat(MDC.get("a")).isNull();
        assertThat(MDC.get("key")).isEqualTo("value");
        verify(callable).call();
    }

    @Test
    public void shouldSetTheAppropriateMdcInCallableWithNoPreviousMdcInstalled() throws Exception {
        // Clear any previous installed MDC
        MDC.clear();

        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                assertThat(MDC.get("a")).isEqualTo("b");
                return null;
            }
        }).when(callable).call();

        Map<String, String> newMdc = singletonMap("a", "b");
        Callable<String> decorated = new MdcScheduledExecutorServiceDelegate.MdcCallable<>(callable, newMdc);

        decorated.call();

        assertThat(MDC.getCopyOfContextMap()).isNull();
        verify(callable).call();
    }
}
