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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.openig.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.json.fluent.JsonValue.*;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.util.ExtensibleFieldMap;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ExpressionTest {

    @DataProvider
    private Object[][] expressions() {
        return new Object[][] {
            { "${1==1}" },
            { "#{myExpression}" },
            { "string-literal" },
            { "ᓱᓴᓐ ᐊᒡᓗᒃᑲᖅ" }, /** Susan Aglukark (singer) */
            { "F" + "\u004F" + "\u0052" + "G" + "\u0045" },
            { "" },
            { "foo\\${a} ${a}${b} foo\\${b}" },
            { "${a} \n${b}" } };
    }


    @Test
    public void bool() throws ExpressionException {
        Expression expr = Expression.valueOf("${1==1}");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(Boolean.class);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void empty() throws ExpressionException {
        Expression expr = Expression.valueOf("string-literal");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("string-literal");
    }

    @Test
    public void emptyString() throws ExpressionException {
        Expression expr = Expression.valueOf("");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("");
    }

    @Test
    public void backslash() throws ExpressionException {
        HashMap<String, String> scope = new HashMap<String, String>();
        scope.put("a", "bar");
        scope.put("b", "bas");
        Expression expr = Expression.valueOf("foo\\${a} ${a}${b} foo\\${b}");
        Object o = expr.eval(scope);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("foo\\bar barbas foo\\bas");
    }

    @Test(dataProvider = "expressions")
    public void toStringTest(final String value) throws ExpressionException {
        final Expression expA = Expression.valueOf(value);
        final Expression expB = Expression.valueOf(expA.toString());
        assertThat(expA.toString()).isEqualTo(expB.toString());
    }

    @Test
    public void scope() throws ExpressionException {
        HashMap<String, String> scope = new HashMap<String, String>();
        scope.put("a", "foo");
        Expression expr = Expression.valueOf("${a}bar");
        Object o = expr.eval(scope);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("foobar");
    }

    @Test
    public void exchangeRequestHeader() throws ExpressionException {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.getHeaders().putSingle("Host", "www.example.com");
        Expression expr = Expression.valueOf("${exchange.request.headers['Host'][0]}");
        String host = expr.eval(exchange, String.class);
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    public void exchangeRequestURI() throws ExpressionException, java.net.URISyntaxException {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://test.com:123/path/to/resource.html");
        Object o = Expression.valueOf("${exchange.request.uri.path}").eval(exchange);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("/path/to/resource.html");
    }

    @Test
    public void exchangeSetAttribute() throws ExpressionException {
        Exchange exchange = new Exchange();
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("foo", "bar");
        Expression expr = Expression.valueOf("${exchange.testmap}");
        expr.set(exchange, map);
        expr = Expression.valueOf("${exchange.testmap.foo}");
        assertThat(expr.eval(exchange, String.class)).isEqualTo("bar");
    }

    @Test
    public void examples() throws Exception {

        // The following are used as examples in the OpenIG documentation, they should all be valid
        Request request = new Request();
        request.setUri("http://wiki.example.com/wordpress/wp-login.php?action=login");
        request.setMethod("POST");
        request.getHeaders().putSingle("host", "wiki.example.com");
        request.getHeaders().putSingle("cookie", "SESSION=value; path=/");

        Response response = new Response();
        response.getHeaders().putSingle("Set-Cookie", "MyCookie=example; path=/");

        Exchange exchange = new Exchange();
        exchange.request = request;
        exchange.response = response;

        Expression expr = Expression.valueOf("${exchange.request.uri.path == '/wordpress/wp-login.php' "
                        + "and exchange.request.form['action'][0] != 'logout'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${toString(exchange.request.uri)}");
        assertThat(expr.eval(exchange, String.class))
                .isEqualTo("http://wiki.example.com/wordpress/wp-login.php?action=login");

        expr = Expression.valueOf("${exchange.request.uri.host == 'wiki.example.com'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${exchange.request.method == 'POST' "
                + "and exchange.request.uri.path == '/wordpress/wp-login.php'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${exchange.request.method != 'GET'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${exchange.request.uri.scheme == 'http'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${not (exchange.response.status == 302 and not empty exchange.session.gotoURL)}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = Expression.valueOf("${exchange.request.headers['host'][0]}");
        assertThat(expr.eval(exchange, String.class)).isEqualTo("wiki.example.com");

        expr = Expression.valueOf("${exchange.request.cookies[keyMatch(exchange.request.cookies,'^SESS.*')][0].value}");
        assertThat(expr.eval(exchange, String.class)).isNotNull();

        expr = Expression.valueOf("${exchange.request.headers['cookie'][0]}");
        assertThat(expr.eval(exchange, String.class)).isNotNull();

        expr = Expression.valueOf("${exchange.response.headers['Set-Cookie'][0]}");
        assertThat(expr.eval(exchange, String.class)).isEqualTo("MyCookie=example; path=/");
    }

    @Test
    public void testAccessingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);
        bfm.put("attribute", "hello");

        assertThat(Expression.valueOf("${legacy}").eval(bfm, String.class)).isEqualTo("OpenIG");
        assertThat(Expression.valueOf("${number}").eval(bfm, Integer.class)).isEqualTo(42);
        assertThat(Expression.valueOf("${readOnly}").eval(bfm, String.class)).isEqualTo("hello");
        assertThat(Expression.valueOf("${attribute}").eval(bfm, String.class)).isEqualTo("hello");
        assertThat(Expression.valueOf("${missing}").eval(bfm, String.class)).isNull();
    }

    @Test
    public void testSettingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);

        Expression.valueOf("${legacy}").set(bfm, "ForgeRock");
        assertThat(bfm.legacy).isEqualTo("ForgeRock");

        Expression.valueOf("${number}").set(bfm, 404);
        assertThat(bfm.getNumber()).isEqualTo(404);

        Expression.valueOf("${readOnly}").set(bfm, "will-be-ignored");
        assertThat(bfm.getReadOnly()).isEqualTo("hello");

        Expression.valueOf("${attribute}").set(bfm, "a-value");
        assertThat(bfm.get("attribute")).isEqualTo("a-value");
    }

    @Test
    public void testUsingIntermediateBean() throws Exception {
        ExternalBean bean = new ExternalBean(new InternalBean("Hello World"));

        assertThat(Expression.valueOf("${internal.value}").eval(bean, String.class)).isEqualTo("Hello World");
        Expression.valueOf("${internal.value}").set(bean, "ForgeRock OpenIG");
        assertThat(bean.getInternal().getValue()).isEqualTo("ForgeRock OpenIG");
    }

    @Test
    public void testImplicitObjectsReferences() throws Exception {
        assertThat(Expression.valueOf("${system['user.home']}").eval(null)).isNotNull();
        assertThat(Expression.valueOf("${env['PATH']}").eval(null)).isNotNull();
    }

    @Test
    public void getNullExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Object o = Expression.valueOf("${exchange.request.entity.string}").eval(exchange);
        assertThat(o).isEqualTo("");
    }

    @Test
    public void getNullExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Object o = Expression.valueOf("${exchange.request.entity.json}").eval(exchange);
        assertThat(o).isNull();
    }

    @Test
    public void getExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("old mcdonald had a farm");
        Object o = Expression.valueOf("${exchange.request.entity.string}").eval(exchange);
        assertThat(o).isEqualTo("old mcdonald had a farm");
    }

    @Test
    public void getExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("{ \"string\" : \"string\", \"int\" : 12345 }");
        Object map = Expression.valueOf("${exchange.request.entity.json}").eval(exchange);
        assertThat(map).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) map).containsOnly(entry("string", "string"), entry("int", 12345));
        Object i = Expression.valueOf("${exchange.request.entity.json.int}").eval(exchange);
        assertThat(i).isEqualTo(12345);
    }

    @Test
    public void setExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Expression.valueOf("${exchange.request.entity.json}").set(exchange, object(field("k1", "v1"),
                                                                               field("k2", 123)));
        assertThat(exchange.request.getEntity()).isNotNull();
        assertThat(exchange.request.getEntity().getString())
                .isEqualTo("{\"k1\":\"v1\",\"k2\":123}");
    }

    @Test
    public void setExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Expression.valueOf("${exchange.request.entity.string}").set(exchange,
                "mary mary quite contrary");
        assertThat(exchange.request.getEntity()).isNotNull();
        assertThat(exchange.request.getEntity().getString()).isEqualTo("mary mary quite contrary");
    }

    public static class BeanFieldMap extends ExtensibleFieldMap {
        public String legacy;

        private int number;
        private String readOnly;

        private BeanFieldMap(final String readOnly) {
            this.readOnly = readOnly;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(final int number) {
            this.number = number;
        }

        public String getReadOnly() {
            return readOnly;
        }

    }

    public static class ExternalBean {
        private InternalBean internal;

        public ExternalBean(final InternalBean internal) {
            this.internal = internal;
        }

        public InternalBean getInternal() {
            return internal;
        }

        public void setInternal(final InternalBean internal) {
            this.internal = internal;
        }
    }

    private static class InternalBean {
        private String value;

        private InternalBean(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @SuppressWarnings("unused")
        public void setValue(final String value) {
            this.value = value;
        }
    }
}
