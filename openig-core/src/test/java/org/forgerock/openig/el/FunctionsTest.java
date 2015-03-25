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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.el;

import static java.lang.String.*;
import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.util.Arrays;

import org.forgerock.http.protocol.Request;
import org.forgerock.openig.handler.router.Files;
import org.forgerock.openig.http.Exchange;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FunctionsTest {

    private Exchange exchange;

    @BeforeMethod
    public void beforeMethod() {
        exchange = new Exchange();
        exchange.request = new Request();
    }

    @Test
    public void toStringTest() throws Exception {
        exchange.request.setUri("http://www.forgerock.org/");
        String o = Expression.valueOf("${toString(exchange.request.uri)}", String.class).eval(exchange);
        assertThat(o).isEqualTo(exchange.request.getUri().toString());
    }

    @Test
    public void keyMatch() throws ExpressionException {
        Request o = Expression.valueOf("${exchange[keyMatch(exchange, '^requ.*')]}", Request.class).eval(exchange);
        assertThat(o).isSameAs(exchange.request);
    }

    @Test
    public void lengthString() throws ExpressionException {
        exchange.put("foo", "12345678901");
        Integer o = Expression.valueOf("${length(exchange.foo)}", Integer.class).eval(exchange);
        assertThat(o).isEqualTo(11);
    }

    @Test
    public void lengthCollection() throws ExpressionException {
        exchange.put("foo", Arrays.asList("1", "2", "3", "4", "5"));
        Integer o = Expression.valueOf("${length(exchange.foo)}", Integer.class).eval(exchange);
        assertThat(o).isEqualTo(5);
    }

    @Test
    public void split() throws ExpressionException {
        String o = Expression.valueOf("${split('a,b,c,d,e', ',')[2]}", String.class).eval(exchange);
        assertThat(o).isEqualTo("c");
    }

    @Test
    public void join() throws ExpressionException {
        String[] s = {"a", "b", "c"};
        exchange.put("foo", s);
        String o = Expression.valueOf("${join(exchange.foo, ',')}", String.class).eval(exchange);
        assertThat(o).isEqualTo("a,b,c");
    }

    @Test
    public void contains() throws ExpressionException {
        String s = "allyoucaneat";
        exchange.put("s", s);
        Boolean o = Expression.valueOf("${contains(exchange.s, 'can')}", Boolean.class).eval(exchange);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void notContains() throws ExpressionException {
        String s = "allyoucaneat";
        exchange.put("s", s);
        Boolean o = Expression.valueOf("${contains(exchange.s, 'foo')}", Boolean.class).eval(exchange);
        assertThat(o).isEqualTo(false);
    }

    @Test
    public void containsSplit() throws ExpressionException {
        String s = "all,you,can,eat";
        exchange.put("s", s);
        Boolean o = Expression.valueOf("${contains(split(exchange.s, ','), 'can')}", Boolean.class).eval(exchange);
        assertThat(o).isEqualTo(true);
    }

    @DataProvider
    private static Object[][] matchData() {
        // @formatter:off
        return new Object[][] {
            { "I am the very model of a modern Major-General",
                "the (.*) model", true, groups("the very model", "very") },
            { "/saml/endpoint", "^/saml", true, groups("/saml") },
            { "/notsaml/endpoint", "^/saml", false, groups() }
        };
        // @formatter:on
    }

    private static String[] groups(String... groups) {
        return groups;
    }

    @Test(dataProvider = "matchData")
    public void matches(String s, String pattern, boolean matches, String[] groups)
            throws Exception {
        exchange.put("s", s);
        Boolean o = Expression.valueOf("${matches(exchange.s, '" + pattern + "')}", Boolean.class).eval(exchange);
        assertThat(o).isEqualTo(matches);
    }

    @Test(dataProvider = "matchData")
    public void matchingGroups(String s, String pattern, boolean matches, String[] groups)
            throws Exception {
        exchange.put("s", s);
        Expression<String[]> stringArrayExpr = Expression.valueOf("${matchingGroups(exchange.s, '" + pattern + "')}",
                String[].class);
        String[] o = stringArrayExpr.eval(exchange);
        if (matches) {
            assertThat(o).isInstanceOf(String[].class);
            String[] ss = o;
            assertThat(ss).isEqualTo(groups);
        } else {
            assertThat(o).isNull();
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
        exchange.put("s", decoded);
        String o = Expression.valueOf("${urlEncode(exchange.s)}", String.class).eval(exchange);
        assertThat(o).isEqualTo(encoded);
    }

    @Test(dataProvider = "urlEncodings")
    public void urlDecode(String decoded, String encoded) throws ExpressionException {
        exchange.put("s", encoded);
        String o = Expression.valueOf("${urlDecode(exchange.s)}", String.class).eval(exchange);
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
        assertThat(Expression.valueOf(format("${read('%s')}", file.getPath()), String.class).eval())
                .isEqualTo("Hello World");
    }

    @Test
    public void testMissingFileReading() throws Exception {
        File file = Files.getRelative(getClass(), "missing.txt");
        assertThat(Expression.valueOf(format("${read('%s')}", file.getPath()), String.class).eval()).isNull();
    }

    @Test
    public void testPropertiesReading() throws Exception {
        File file = Files.getRelativeFile(getClass(), "configuration.properties");
        String str = format("${readProperties('%s')['key']}", file.getPath());
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

}
