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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.el;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.net.URI;
import java.util.Arrays;

import org.forgerock.openig.handler.router.Files;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class FunctionTest {

    private Exchange exchange;

    @BeforeMethod
    public void beforeMethod() {
        exchange = new Exchange();
        exchange.request = new Request();
    }

    @Test
    public void toStringTest() throws ExpressionException {
        exchange.request.setUri(URI.create("http://www.forgerock.org/"));
        Object o = new Expression("${toString(exchange.request.uri)}").eval(exchange);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo(exchange.request.getUri().toString());
    }

    @Test
    public void keyMatch() throws ExpressionException {
        Object o = new Expression("${exchange[keyMatch(exchange, '^requ.*')]}").eval(exchange);
        assertThat(o).isInstanceOf(Request.class);
        assertThat(o).isSameAs(exchange.request);
    }

    @Test
    public void lengthString() throws ExpressionException {
        exchange.put("foo", "12345678901");
        Object o = new Expression("${length(exchange.foo)}").eval(exchange);
        assertThat(o).isInstanceOf(Integer.class);
        assertThat(o).isEqualTo(11);
    }

    @Test
    public void lengthCollection() throws ExpressionException {
        exchange.put("foo", Arrays.asList("1", "2", "3", "4", "5"));
        Object o = new Expression("${length(exchange.foo)}").eval(exchange);
        assertThat(o).isInstanceOf(Integer.class);
        assertThat(o).isEqualTo(5);
    }

    @Test
    public void split() throws ExpressionException {
        Object o = new Expression("${split('a,b,c,d,e', ',')[2]}").eval(exchange);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("c");
    }

    @Test
    public void join() throws ExpressionException {
        String[] s = {"a", "b", "c"};
        exchange.put("foo", s);
        Object o = new Expression("${join(exchange.foo, ',')}").eval(exchange);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("a,b,c");
    }

    @Test
    public void contains() throws ExpressionException {
        String s = "allyoucaneat";
        exchange.put("s", s);
        Object o = new Expression("${contains(exchange.s, 'can')}").eval(exchange);
        assertThat(o).isInstanceOf(Boolean.class);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void notContains() throws ExpressionException {
        String s = "allyoucaneat";
        exchange.put("s", s);
        Object o = new Expression("${contains(exchange.s, 'foo')}").eval(exchange);
        assertThat(o).isInstanceOf(Boolean.class);
        assertThat(o).isEqualTo(false);
    }

    @Test
    public void containsSplit() throws ExpressionException {
        String s = "all,you,can,eat";
        exchange.put("s", s);
        Object o = new Expression("${contains(split(exchange.s, ','), 'can')}").eval(exchange);
        assertThat(o).isInstanceOf(Boolean.class);
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
        Object o = new Expression("${matches(exchange.s, '" + pattern + "')}").eval(exchange);
        assertThat(o).isInstanceOf(Boolean.class);
        Boolean b = (Boolean) o;
        assertThat(b).isEqualTo(matches);
    }

    @Test(dataProvider = "matchData")
    public void matchingGroups(String s, String pattern, boolean matches, String[] groups)
            throws Exception {
        exchange.put("s", s);
        Object o =
                new Expression("${matchingGroups(exchange.s, '" + pattern + "')}").eval(exchange);
        if (matches) {
            assertThat(o).isInstanceOf(String[].class);
            String[] ss = (String[]) o;
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
        Object o = new Expression("${urlEncode(exchange.s)}").eval(exchange);
        assertThat(o).isEqualTo(encoded);
    }

    @Test(dataProvider = "urlEncodings")
    public void urlDecode(String decoded, String encoded) throws ExpressionException {
        exchange.put("s", encoded);
        Object o = new Expression("${urlDecode(exchange.s)}").eval(exchange);
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
        assertThat(new Expression(format("${encodeBase64(%s)}", original)).eval(null))
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
        assertThat(new Expression(format("${decodeBase64(%s)}", original)).eval(null))
                .isEqualTo(decoded);
    }

    @Test
    public void testFileReading() throws Exception {
        File file = Files.getRelativeFile(getClass(), "readme.txt");
        assertThat(new Expression(format("${read('%s')}", file.getPath())).eval(null))
                .isEqualTo("Hello World");
    }

    @Test
    public void testMissingFileReading() throws Exception {
        File file = Files.getRelative(getClass(), "missing.txt");
        assertThat(new Expression(format("${read('%s')}", file.getPath())).eval(null)).isNull();
    }

    @Test
    public void testPropertiesReading() throws Exception {
        File file = Files.getRelativeFile(getClass(), "configuration.properties");
        assertThat(new Expression(format("${readProperties('%s')['key']}", file.getPath())).eval(null))
                .isEqualTo("some value");
    }
}
