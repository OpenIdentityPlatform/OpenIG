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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.openig.heap.Name;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class LogSinkLoggerTest {

    private final static String NAME_STR = "a.logger.name";
    private final static Name NAME_IG = Name.of(NAME_STR);

    @Mock
    private LogSink logSink;

    private LogSinkLoggerFactory factory = new LogSinkLoggerFactory();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        factory.setLogSink(logSink);
    }

    @Test
    public void shouldTraceIfEnabled() throws Exception {
        when(logSink.isLoggable(NAME_IG, LogLevel.TRACE)).thenReturn(true);

        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isTraceEnabled()).isTrue();

        logger.trace("Hello");
        logger.trace("Hello {}", "World");
        logger.trace("Hello {}{}", "World", "!");
        logger.trace("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.trace("Hello failed", new Exception("Boom"));

        verify(logSink, times(6)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotTraceIfDisabled() throws Exception {
        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isTraceEnabled()).isFalse();

        logger.trace("Hello");
        logger.trace("Hello {}", "World");
        logger.trace("Hello {}{}", "World", "!");
        logger.trace("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.trace("Hello failed", new Exception("Boom"));

        verify(logSink, never()).log(any(LogEntry.class));
    }

    @Test
    public void shouldDebugIfEnabled() throws Exception {
        when(logSink.isLoggable(NAME_IG, LogLevel.DEBUG)).thenReturn(true);

        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isDebugEnabled()).isTrue();

        logger.debug("Hello");
        logger.debug("Hello {}", "World");
        logger.debug("Hello {}{}", "World", "!");
        logger.debug("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.debug("Hello failed", new Exception("Boom"));

        verify(logSink, times(6)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotDebugIfDisabled() throws Exception {
        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isDebugEnabled()).isFalse();

        logger.debug("Hello");
        logger.debug("Hello {}", "World");
        logger.debug("Hello {}{}", "World", "!");
        logger.debug("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.debug("Hello failed", new Exception("Boom"));

        verify(logSink, never()).log(any(LogEntry.class));
    }

    @Test
    public void shouldInfoIfEnabled() throws Exception {
        when(logSink.isLoggable(NAME_IG, LogLevel.INFO)).thenReturn(true);

        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isInfoEnabled()).isTrue();

        logger.info("Hello");
        logger.info("Hello {}", "World");
        logger.info("Hello {}{}", "World", "!");
        logger.info("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.info("Hello failed", new Exception("Boom"));

        verify(logSink, times(6)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotInfoIfDisabled() throws Exception {
        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isInfoEnabled()).isFalse();

        logger.info("Hello");
        logger.info("Hello {}", "World");
        logger.info("Hello {}{}", "World", "!");
        logger.info("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.info("Hello failed", new Exception("Boom"));

        verify(logSink, never()).log(any(LogEntry.class));
    }

    @Test
    public void shouldWarnIfEnabled() throws Exception {
        when(logSink.isLoggable(NAME_IG, LogLevel.WARNING)).thenReturn(true);

        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isWarnEnabled()).isTrue();

        logger.warn("Hello");
        logger.warn("Hello {}", "World");
        logger.warn("Hello {}{}", "World", "!");
        logger.warn("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.warn("Hello failed", new Exception("Boom"));

        verify(logSink, times(6)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotWarnIfDisabled() throws Exception {
        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isWarnEnabled()).isFalse();

        logger.warn("Hello");
        logger.warn("Hello {}", "World");
        logger.warn("Hello {}{}", "World", "!");
        logger.warn("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.warn("Hello failed", new Exception("Boom"));

        verify(logSink, never()).log(any(LogEntry.class));
    }

    @Test
    public void shouldErrorIfEnabled() throws Exception {
        when(logSink.isLoggable(NAME_IG, LogLevel.ERROR)).thenReturn(true);

        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isErrorEnabled()).isTrue();

        logger.error("Hello");
        logger.error("Hello {}", "World");
        logger.error("Hello {}{}", "World", "!");
        logger.error("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.error("Hello failed", new Exception("Boom"));

        verify(logSink, times(6)).log(any(LogEntry.class));
    }

    @Test
    public void shouldNotErrorIfDisabled() throws Exception {
        Logger logger = factory.getLogger(NAME_STR);
        assertThat(logger.isErrorEnabled()).isFalse();

        logger.error("Hello");
        logger.error("Hello {}", "World");
        logger.error("Hello {}{}", "World", "!");
        logger.error("Hello {}{} {}", "World", "!", "Yeehaa");
        logger.error("Hello failed", new Exception("Boom"));

        verify(logSink, never()).log(any(LogEntry.class));
    }
}
