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

package org.forgerock.openig.regex;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Basically a {@code StreamPatternMatches} is a {@link org.forgerock.openig.regex.StringPatternMatches} that supports
 * multi-line: it wraps the given reader in a {@link java.io.BufferedReader} and consume it line by line.
 * For each line, it creates a new {@link org.forgerock.openig.regex.StringPatternMatches} and returns the
 * produced {@link java.util.regex.Matcher}s.
 */
@SuppressWarnings("javadoc")
public class StreamPatternMatchesTest {

    @Mock
    private Reader reader;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMultiLinePatternNotMatching() throws Exception {
        Pattern pattern = Pattern.compile("c");
        StreamPatternMatches matches = new StreamPatternMatches(Readers.reader("aab", "aab"), asList(pattern), true);

        assertThat(matches.hasNext()).isFalse();
    }

    @Test
    public void testMultiLinePatternMatching() throws Exception {
        Pattern pattern = Pattern.compile("a+b");
        // Needs to set discard to false otherwise, only the first matching result is returned
        StreamPatternMatches matches = new StreamPatternMatches(Readers.reader("aab", "aab"), asList(pattern), false);

        Matcher matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("aab");
        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");

        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("aab");
        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");

        assertThat(matches.hasNext()).isFalse();
    }

    @Test
    public void testCLoseIsPropagated() throws Exception {
        Pattern pattern = Pattern.compile(".*");
        StreamPatternMatches matches = new StreamPatternMatches(reader, asList(pattern), true);

        matches.close();

        verify(reader).close();
    }

    @Test
    public void testMultiLineMultiPatternMatching() throws Exception {

        StreamPatternMatches matches = new StreamPatternMatches(
                Readers.reader("aab", "aabc"),
                asList(
                        Pattern.compile("ab"),
                        Pattern.compile("bc")),
                false);

        Matcher matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");
        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");
        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("bc");

        assertThat(matches.hasNext()).isFalse();
    }

    @Test
    public void testMultiLineMultiPatternMatchingAndDiscrad() throws Exception {

        StreamPatternMatches matches = new StreamPatternMatches(
                Readers.reader("aab", "aabc"),
                asList(
                        Pattern.compile("ab"),
                        Pattern.compile("bc")),
                true);

        Matcher matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");
        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("bc");

        assertThat(matches.hasNext()).isFalse();
    }

}
