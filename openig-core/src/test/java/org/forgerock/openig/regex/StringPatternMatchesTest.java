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

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.annotations.Test;

public class StringPatternMatchesTest {

    @Test
    public void testWithNoMatchingMatchers() throws Exception {
        Pattern pattern = compile("c");
        StringPatternMatches matches = new StringPatternMatches("aaab", asList(pattern), true);

        assertThat(matches.hasNext()).isFalse();
    }

    @Test(expectedExceptions = NoSuchElementException.class)
    public void testWithNoMatchingMatchersThrowException() throws Exception {
        Pattern pattern = compile("c");
        StringPatternMatches matches = new StringPatternMatches("aaab", asList(pattern), true);

        matches.next();
    }

    @Test
    public void testMatchingMatchersAreDiscarded() throws Exception {
        Pattern pattern = compile("a*b");
        StringPatternMatches matches = new StringPatternMatches("aaab", asList(pattern), true);

        Matcher matcher = matches.next();
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group()).isEqualTo("aaab");
        assertThat(matches.hasNext()).isFalse();
    }

    @Test
    public void testMatchingMatchersAreNotDiscarded() throws Exception {
        Pattern pattern = compile("a+b");
        StringPatternMatches matches = new StringPatternMatches("aaab", asList(pattern), false);

        Matcher matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("aaab");

        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("aab");

        matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");

        assertThat(matches.hasNext()).isFalse();
    }

    @Test
    public void testDiscardedPatternsAreIgnoredInMultiPatternExpression() throws Exception {
        StringPatternMatches matches = new StringPatternMatches(
                "ab",
                asList(compile("c"),compile("a+b")),
                true
        );

        assertThat(matches.hasNext()).isTrue();

        // Consume the matcher
        Matcher matcher = matches.next();
        assertThat(matcher.group()).isEqualTo("ab");

        assertThat(matches.hasNext()).isFalse();
    }
}
