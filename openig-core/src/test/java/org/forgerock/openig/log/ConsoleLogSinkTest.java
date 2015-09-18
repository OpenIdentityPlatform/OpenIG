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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConsoleLogSinkTest {

    // Store System.*** values
    private PrintStream systemOut;
    private PrintStream systemErr;

    private ByteArrayOutputStream baos;
    private ByteArrayOutputStream baes;

    @BeforeMethod
    public void setUp() throws Exception {
        systemOut = System.out;
        systemErr = System.err;
        baos = new ByteArrayOutputStream();
        baes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true));
        System.setErr(new PrintStream(baes, true));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        System.setOut(systemOut);
        System.setErr(systemErr);
    }

    @DataProvider
    public static Object[][] levelsLoggedToOut() {
        // @Checkstyle:off
        return new Object[][] {
                {LogLevel.ALL},
                {LogLevel.TRACE},
                {LogLevel.DEBUG},
                {LogLevel.STAT},
                {LogLevel.CONFIG},
                {LogLevel.INFO}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "levelsLoggedToOut")
    public void shouldLogEntriesOnSystemOut(final LogLevel level) throws Exception {
        ConsoleLogSink sink = new ConsoleLogSink();
        sink.setLevel(LogLevel.ALL);
        sink.setStream(ConsoleLogSink.Stream.AUTO);

        sink.log(new LogEntry(Name.of("Source"), level, "Hello OpenIG"));

        assertThat(baos.toString()).containsSequence(" Source", "Hello OpenIG", "-----------------");
        assertThat(baes.toString()).isNullOrEmpty();
    }

    @DataProvider
    public static Object[][] levelsLoggedToErr() {
        // @Checkstyle:off
        return new Object[][] {
                {LogLevel.WARNING},
                {LogLevel.ERROR}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "levelsLoggedToErr")
    public void shouldLogEntriesOnSystemErr(final LogLevel level) throws Exception {
        ConsoleLogSink sink = new ConsoleLogSink();
        sink.setLevel(LogLevel.ALL);
        sink.setStream(ConsoleLogSink.Stream.AUTO);

        sink.log(new LogEntry(Name.of("Source"), level, "Hello OpenIG"));
        assertThat(baos.toString()).isNullOrEmpty();
        assertThat(baes.toString()).containsSequence(" Source", "Hello OpenIG", "-----------------");
    }

    @DataProvider
    public static Object[][] allLevels() {
        // @Checkstyle:off
        return new Object[][] {
                {LogLevel.TRACE},
                {LogLevel.DEBUG},
                {LogLevel.STAT},
                {LogLevel.CONFIG},
                {LogLevel.INFO},
                {LogLevel.WARNING},
                {LogLevel.ERROR}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "allLevels")
    public void shouldNotLogAnything(final LogLevel level) throws Exception {
        ConsoleLogSink sink = new ConsoleLogSink();

        sink.setLevel(LogLevel.OFF);

        sink.log(new LogEntry(Name.of("Source"), level, "Hello OpenIG"));
        assertThat(baos.toString()).isNullOrEmpty();
        assertThat(baes.toString()).isNullOrEmpty();
    }

    @Test
    public void shouldPrintSyntheticExceptionStack() throws Exception {
        ConsoleLogSink sink = new ConsoleLogSink();
        sink.setLevel(LogLevel.INFO);
        sink.log(new LogEntry(Name.of("Source"), "throwable", LogLevel.INFO, "Hello OpenIG", new Exception("Boom")));
        assertThat(baes.toString()).containsSequence("(INFO) Source",
                                                     "Hello OpenIG",
                                                     "[                Exception] > Boom",
                                                     "-----------------");
    }

    @Test
    public void shouldPrintSyntheticExceptionStack2() throws Exception {
        Throwable t = new IOException("IO", new HeapException("Heap", new IncompatibleClassChangeError()));
        ConsoleLogSink sink = new ConsoleLogSink();
        sink.setLevel(LogLevel.INFO);

        sink.log(new LogEntry(Name.of("Source"), "throwable", LogLevel.INFO, "Hello OpenIG", t));

        assertThat(baes.toString()).containsSequence("(INFO) Source",
                                                     "Hello OpenIG",
                                                     "[              IOException] > IO",
                                                     "[            HeapException] > Heap",
                                                     "[IncompatibleClassChangeError] > null",
                                                     "-----------------");
    }

    @DataProvider
    public static Object[][] sinkStackTraceEnabledLevels() {
        // @Checkstyle:off
        return new Object[][] {
                {LogLevel.TRACE},
                {LogLevel.DEBUG}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "sinkStackTraceEnabledLevels")
    public void shouldPrintStackTraceAtDebugLevel(final LogLevel sinkLevel) throws Exception {
        ConsoleLogSink sink = new ConsoleLogSink();
        sink.setLevel(sinkLevel);

        // LogEntry level is not used when choosing to print (or not) the stack trace, so we use
        // a level that will (at least) trigger the entry log
        sink.log(new LogEntry(Name.of("Source"), "throwable", LogLevel.INFO, "Hello OpenIG", new Exception("Boom")));
        assertThat(baes.toString()).containsSequence("(INFO) Source",
                                                     "Hello OpenIG",
                                                     "[                Exception] > Boom",
                                                     "java.lang.Exception: Boom",
                                                     "-----------------");
    }
}
