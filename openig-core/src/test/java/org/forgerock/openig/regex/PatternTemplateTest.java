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

import static org.assertj.core.api.Assertions.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.annotations.Test;

/**
 * The {{PatternTemplate}} class is intended to be used in the case of a pattern matching context.
 */
@SuppressWarnings("javadoc")
public class PatternTemplateTest {
    @Test
    public void testTemplateSubstitutionWithoutGroupCapture() throws Exception {
        PatternTemplate template = new PatternTemplate("matched");

        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("matched");
    }

    @Test
    public void testTemplateSubstitutionWithGroup0Capture() throws Exception {
        PatternTemplate template = new PatternTemplate("$0");

        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("Hello");
    }

    @Test
    public void testTemplateSubstitutionWithGroup1Capture() throws Exception {
        PatternTemplate template = new PatternTemplate("$1");

        Pattern pattern = Pattern.compile("(Hel+)o");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("Hell");
    }

    @Test
    public void testTemplateSubstitutionWithTwoDigitGroupCapture() throws Exception {
        PatternTemplate template = new PatternTemplate("$10");

        Pattern pattern = Pattern.compile("(((He(l+))(o)) (((Op)(en))(IG)))");
        Matcher matcher = pattern.matcher("Hello OpenIG");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("IG");
    }

    @Test
    public void testTemplateSubstitutionWithMixedStringAndGroupCapture() throws Exception {
        PatternTemplate template = new PatternTemplate("$1o OpenIG");

        Pattern pattern = Pattern.compile("(Hel+)o");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("Hello OpenIG");
    }

    @Test
    public void testTemplateSubstitutionWithEscapedDollar() throws Exception {
        PatternTemplate template = new PatternTemplate("\\$1");

        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("$1");
    }

    @Test
    public void testTemplateSubstitutionWithEscapedSlash() throws Exception {
        PatternTemplate template = new PatternTemplate("a\\\\b");

        Pattern pattern = Pattern.compile(".*");
        Matcher matcher = pattern.matcher("Hello");

        assertThat(matcher.matches()).isTrue();
        assertThat(template.applyTo(matcher.toMatchResult()))
                .isEqualTo("a\\b");
    }
}
