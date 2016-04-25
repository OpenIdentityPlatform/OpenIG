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

package org.forgerock.openig.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.forgerock.openig.heap.HeapUtilsTest;
import org.forgerock.openig.heap.Heaplet;
import org.forgerock.openig.heap.Name;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class Slf4jLogSinkTest {

    private static final Name SOURCE_NAME = Name.of("{Filter}/Source");
    private static final String MESSAGE = "message";

    @Mock
    private org.slf4j.Logger logger;

    @Mock
    private org.slf4j.ILoggerFactory factory;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(factory.getLogger(anyString())).thenReturn(logger);
    }

    @Test
    public void shouldCreateSlf4jLogSink() throws Exception {
        Heaplet heaplet = new Slf4jLogSink.Heaplet();
        assertThat(heaplet.create(Name.of("this"),
                                  json(object(field("base", "org.forgerock.openig"))),
                                  HeapUtilsTest.buildDefaultHeap()))
                .isNotNull();
    }

    @DataProvider
    public static Object[][] levels() {
        // @Checkstyle:off
        return new Object[][] {
                {LogLevel.TRACE, "obiwan", Expectation.TRACE},
                {LogLevel.DEBUG, "anakin", Expectation.DEBUG},
                {LogLevel.STAT, "padme", Expectation.INFO},
                {LogLevel.CONFIG, "luke", Expectation.INFO},
                {LogLevel.INFO, "rei", Expectation.INFO},
                {LogLevel.WARNING, "han", Expectation.WARN},
                {LogLevel.ERROR, "chewie", Expectation.ERROR},
                {LogLevel.ALL, "lando", Expectation.IGNORE},
                {LogLevel.OFF, "leia", Expectation.IGNORE},
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "levels")
    public void shouldWriteAtExpectedLevel(LogLevel level, String type, Expectation expectation) throws Exception {
        // Given
        LogSink sink = new Slf4jLogSink("org.forgerock.openig", factory);

        // When
        sink.log(new LogEntry(SOURCE_NAME, type, level, MESSAGE, null));

        // Then
        expectation.isVerified(logger, type, MESSAGE, null);
        verify(factory).getLogger("org.forgerock.openig.filter-source");
    }

    @Test(dataProvider = "levels")
    public void shouldLogExceptionInData(LogLevel level, String type, Expectation expectation) throws Exception {
        // Given
        LogSink sink = new Slf4jLogSink("org.forgerock.openig.", factory);
        Exception exception = new Exception("boom");

        // When
        sink.log(new LogEntry(SOURCE_NAME, type, level, MESSAGE, exception));

        // Then
        expectation.isVerified(logger, type, MESSAGE, exception);
        verify(factory).getLogger("org.forgerock.openig.filter-source");
    }

    private enum Expectation {
        TRACE {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                ArgumentCaptor<Marker> capture = ArgumentCaptor.forClass(Marker.class);
                verify(logger).trace(capture.capture(), eq(message), same(data));
                assertThat(capture.getValue().getName()).isEqualTo(type);
            }
        },

        DEBUG {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                ArgumentCaptor<Marker> capture = ArgumentCaptor.forClass(Marker.class);
                verify(logger).debug(capture.capture(), eq(message), same(data));
                assertThat(capture.getValue().getName()).isEqualTo(type);
            }
        },

        INFO {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                ArgumentCaptor<Marker> capture = ArgumentCaptor.forClass(Marker.class);
                verify(logger).info(capture.capture(), eq(message), same(data));
                assertThat(capture.getValue().getName()).isEqualTo(type);
            }
        },

        WARN {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                ArgumentCaptor<Marker> capture = ArgumentCaptor.forClass(Marker.class);
                verify(logger).warn(capture.capture(), eq(message), same(data));
                assertThat(capture.getValue().getName()).isEqualTo(type);
            }
        },

        ERROR {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                ArgumentCaptor<Marker> capture = ArgumentCaptor.forClass(Marker.class);
                verify(logger).error(capture.capture(), eq(message), same(data));
                assertThat(capture.getValue().getName()).isEqualTo(type);
            }
        },

        IGNORE {
            @Override
            public void isVerified(Logger logger, String type, String message, Object data) {
                verifyZeroInteractions(logger);
            }
        };

        @Override
        public String toString() {
            return String.format("logger.%s()", name().toLowerCase());
        }

        abstract void isVerified(Logger logger, String type, String message, Object data);
    }
}
