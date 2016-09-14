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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.el;

import static java.lang.String.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.el.Bindings.bindings;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.openig.handler.router.Files;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FunctionsTest {

    private Map<String, Object> attributes;
    private Request request;
    private Bindings bindings;

    @BeforeMethod
    public void beforeMethod() {
        attributes = new HashMap<>();
        request = new Request();
        bindings = bindings().bind("attributes", attributes).bind("request", request);
    }

    @Test
    public void toStringTest() throws Exception {
        request.setUri("http://www.forgerock.org/");
        String o = Expression.valueOf("${toString(request.uri)}", String.class).eval(bindings);
        assertThat(o).isEqualTo(request.getUri().toString());
    }

    @Test
    public void keyMatch() throws ExpressionException {
        attributes.put("bjensen", "Barbara Jensen");
        attributes.put("bmaddox", "Barbara Maddox");

        String o = Expression.valueOf("${attributes[keyMatch(attributes, '^bjense.*')]}",
                                      String.class).eval(bindings);
        // Returns the first key found in a map that matches the regular expression
        assertThat(o).isEqualTo(attributes.get("bjensen"));
    }

    @Test
    public void lengthString() throws ExpressionException {
        attributes.put("foo", "12345678901");
        Integer o = Expression.valueOf("${length(attributes.foo)}", Integer.class).eval(bindings);
        assertThat(o).isEqualTo(11);
    }

    @Test
    public void lengthCollection() throws ExpressionException {
        attributes.put("foo", Arrays.asList("1", "2", "3", "4", "5"));
        Integer o = Expression.valueOf("${length(attributes.foo)}", Integer.class).eval(bindings);
        assertThat(o).isEqualTo(5);
    }

    @Test
    public void split() throws ExpressionException {
        String o = Expression.valueOf("${split('a,b,c,d,e', ',')[2]}", String.class).eval(bindings);
        assertThat(o).isEqualTo("c");
    }

    @Test
    public void join() throws ExpressionException {
        String[] s = {"a", "b", "c"};
        attributes.put("foo", s);
        String o = Expression.valueOf("${join(attributes.foo, ',')}", String.class).eval(bindings);
        assertThat(o).isEqualTo("a,b,c");
    }

    @Test
    public void contains() throws ExpressionException {
        String s = "allyoucaneat";
        attributes.put("s", s);
        Boolean o = Expression.valueOf("${contains(attributes.s, 'can')}", Boolean.class).eval(bindings);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void notContains() throws ExpressionException {
        String s = "allyoucaneat";
        attributes.put("s", s);
        Boolean o = Expression.valueOf("${contains(attributes.s, 'foo')}", Boolean.class).eval(bindings);
        assertThat(o).isEqualTo(false);
    }

    @Test
    public void containsSplit() throws ExpressionException {
        String s = "all,you,can,eat";
        attributes.put("s", s);
        Boolean o = Expression.valueOf("${contains(split(attributes.s, ','), 'can')}", Boolean.class)
                              .eval(bindings);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void integer() throws ExpressionException {
        Object o = Expression.valueOf("${integer('42')}", Object.class).eval();
        assertThat(o).isInstanceOf(Integer.class).isEqualTo(42);
    }

    @Test(dataProvider = "integerNull")
    public void integerBadInput(String value) throws ExpressionException {
        Object o = Expression.valueOf("${integer(" + value + ")}", Object.class).eval();
        assertThat(o).isNull();
    }

    @DataProvider
    private static Object[][] integerNull() {
        // @formatter:off
        return new Object[][] {
            { "'foo'" },
            { "'1 + 1'" },
            { null }
        };
        // @formatter:on
    }

    @Test(dataProvider = "booleanData")
    public void bool(String value, boolean expected) throws ExpressionException {
        Object o = Expression.valueOf("${bool('" + value + "')}", Object.class).eval();
        assertThat(o).isInstanceOf(Boolean.class).isEqualTo(expected);
    }

    @DataProvider
    private static Object[][] booleanData() {
        // @formatter:off
        return new Object[][] {
            { "true", true },
            { "TRue", true },
            { "false", false },
            { "FalsE", false },
            { null, false },
            { "foo", false }
        };
        // @formatter:on
    }

    @DataProvider
    private static Object[][] matchData() {
        // @formatter:off
        return new Object[][] {
            { "'I am the very model of a modern Major-General'",
                "the (.*) model", true, groups("the very model", "very") },
            { "'/saml/endpoint'", "^/saml", true, groups("/saml") },
            { "'/notsaml/endpoint'", "^/saml", false, groups() }
        };
        // @formatter:on
    }

    private static String[] groups(String... groups) {
        return groups;
    }

    @Test(dataProvider = "matchData")
    public void matches(String input, String pattern, boolean shouldMatch, String[] ignored) throws Exception {
        Boolean o = Expression.valueOf(format("${matches(%s, '%s')}", input, pattern), Boolean.class)
                              .eval(bindings);
        assertThat(o).isEqualTo(shouldMatch);
    }

    @Test(dataProvider = "matchData")
    public void matchingGroups(String input, String pattern, boolean shouldMatch, String[] expectedGroups)
            throws Exception {

        Expression<String[]> stringArrayExpr = Expression.valueOf(format("${matchingGroups(%s, '%s')}", input, pattern),
                                                                  String[].class);
        final String[] groups = stringArrayExpr.eval(bindings);
        if (shouldMatch) {
            assertThat(groups).isEqualTo(expectedGroups);
        } else {
            assertThat(groups).isNull();
        }
    }

    @DataProvider
    private static Object[][] urlEncodings() {
        // @formatter:off
        return new Object[][] {
            { null, null },
            { "", "" },
            { "needs !@#$ encoding", "needs+%21%40%23%24+encoding" }
        };
        // @formatter:on
    }

    @Test(dataProvider = "urlEncodings")
    public void urlEncode(String decoded, String encoded) throws ExpressionException {
        attributes.put("s", decoded);
        String o = Expression.valueOf("${urlEncode(attributes.s)}", String.class).eval(bindings);
        assertThat(o).isEqualTo(encoded);
    }

    @Test(dataProvider = "urlEncodings")
    public void urlDecode(String decoded, String encoded) throws ExpressionException {
        attributes.put("s", encoded);
        String o = Expression.valueOf("${urlDecode(attributes.s)}", String.class).eval(bindings);
        assertThat(o).isEqualTo(decoded);
    }

    @DataProvider
    public static Object[][] base64EncodingValues() {
        // @Checkstyle:off
        return new Object[][] {
                // Don't forget the enclosing ' (quote)
                {"'This is a very long string'", "VGhpcyBpcyBhIHZlcnkgbG9uZyBzdHJpbmc="},
                {"'hello'", "aGVsbG8="},
                {"''", ""},
                {null, null}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "base64EncodingValues")
    public void testBase64Encoding(final String original, final String encoded) throws Exception {
        assertThat(Expression.valueOf(format("${encodeBase64(%s)}", original), String.class).eval())
                .isEqualTo(encoded);
    }

    @DataProvider
    public static Object[][] base64DecodingValues() {
        // @Checkstyle:off
        return new Object[][] {
                // Don't forget the enclosing ' (quote)
                {"'VGhpcyBpcyBhIHZlcnkgbG9uZyBzdHJpbmc='", "This is a very long string"},
                {"'aGVsbG8='", "hello"},
                {"'VGhpcyBpcyB'", null}, // Invalid value
                {"''", ""},
                {null, null}
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "base64DecodingValues")
    public void testBase64Decoding(final String original, final String decoded) throws Exception {
        String str = format("${decodeBase64(%s)}", original);
        assertThat(Expression.valueOf(str, String.class).eval())
                .isEqualTo(decoded);
    }

    @Test
    public void testFileReading() throws Exception {
        File file = Files.getRelativeFile(getClass(), "readme.txt");
        assertThat(Expression.valueOf(format("${read('%s')}", escapeBackslashes(file.getPath())),
                                      String.class).eval())
                .isEqualTo("Hello World");
    }

    @Test
    public void testMissingFileReading() throws Exception {
        File file = Files.getRelative(getClass(), "missing.txt");
        assertThat(Expression.valueOf(format("${read('%s')}", escapeBackslashes(file.getPath())),
                                      String.class).eval()).isNull();
    }

    @Test
    public void testPropertiesReading() throws Exception {
        File file = Files.getRelativeFile(getClass(), "configuration.properties");
        String str = format("${readProperties('%s')['key']}", escapeBackslashes(file.getPath()));
        Expression<String> expr = Expression.valueOf(str, String.class);
        assertThat(expr.eval()).isEqualTo("some value");
    }

    @Test
    public void array() throws Exception {
        String o = Expression.valueOf("${array('a', 'b', 'c')[1]}", String.class).eval();
        assertThat(o).isEqualTo("b");
    }

    @Test
    public void combineArrayAndJoin() throws Exception {
        String o = Expression.valueOf("${join(array('a', 'b', 'c'), ':')}", String.class).eval();
        assertThat(o).isEqualTo("a:b:c");
    }

    /**
     * EL doesn't accept windows style path (with backslashes).
     * We need to protect (escape) them in order to have parse-able expressions.
     */
    private static String escapeBackslashes(final String value) {
        return value.replace("\\", "\\\\");
    }

}
