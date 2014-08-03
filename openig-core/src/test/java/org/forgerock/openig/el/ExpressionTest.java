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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.forgerock.json.fluent.JsonValue.*;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.util.ExtensibleFieldMap;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ExpressionTest {

    @Test
    public void bool() throws ExpressionException {
        Expression expr = new Expression("${1==1}");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(Boolean.class);
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void empty() throws ExpressionException {
        Expression expr = new Expression("string-literal");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("string-literal");
    }

    @Test
    public void emptyString() throws ExpressionException {
        Expression expr = new Expression("");
        Object o = expr.eval(null); // no scope required for non-resolving expression
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("");
    }

    @Test
    public void backslash() throws ExpressionException {
        HashMap<String, String> scope = new HashMap<String, String>();
        scope.put("a", "bar");
        scope.put("b", "bas");
        Expression expr = new Expression("foo\\${a} ${a}${b} foo\\${b}");
        Object o = expr.eval(scope);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("foo\\bar barbas foo\\bas");
    }


    @Test
    public void scope() throws ExpressionException {
        HashMap<String, String> scope = new HashMap<String, String>();
        scope.put("a", "foo");
        Expression expr = new Expression("${a}bar");
        Object o = expr.eval(scope);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("foobar");
    }

    @Test
    public void exchangeRequestHeader() throws ExpressionException {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.getHeaders().putSingle("Host", "www.example.com");
        Expression expr = new Expression("${exchange.request.headers['Host'][0]}");
        String host = expr.eval(exchange, String.class);
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    public void exchangeRequestURI() throws ExpressionException, java.net.URISyntaxException {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri("http://test.com:123/path/to/resource.html");
        Object o = new Expression("${exchange.request.uri.path}").eval(exchange);
        assertThat(o).isInstanceOf(String.class);
        assertThat(o).isEqualTo("/path/to/resource.html");
    }

    @Test
    public void exchangeSetAttribute() throws ExpressionException {
        Exchange exchange = new Exchange();
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("foo", "bar");
        Expression expr = new Expression("${exchange.testmap}");
        expr.set(exchange, map);
        expr = new Expression("${exchange.testmap.foo}");
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

        Expression expr = new Expression("${exchange.request.uri.path == '/wordpress/wp-login.php' "
                        + "and exchange.request.form['action'][0] != 'logout'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${toString(exchange.request.uri)}");
        assertThat(expr.eval(exchange, String.class))
                .isEqualTo("http://wiki.example.com/wordpress/wp-login.php?action=login");

        expr = new Expression("${exchange.request.uri.host == 'wiki.example.com'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${exchange.request.method == 'POST' "
                + "and exchange.request.uri.path == '/wordpress/wp-login.php'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${exchange.request.method != 'GET'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${exchange.request.uri.scheme == 'http'}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${not (exchange.response.status == 302 and not empty exchange.session.gotoURL)}");
        assertThat(expr.eval(exchange, Boolean.class)).isTrue();

        expr = new Expression("${exchange.request.headers['host'][0]}");
        assertThat(expr.eval(exchange, String.class)).isEqualTo("wiki.example.com");

        expr = new Expression("${exchange.request.cookies[keyMatch(exchange.request.cookies,'^SESS.*')][0].value}");
        assertThat(expr.eval(exchange, String.class)).isNotNull();

        expr = new Expression("${exchange.request.headers['cookie'][0]}");
        assertThat(expr.eval(exchange, String.class)).isNotNull();

        expr = new Expression("${exchange.response.headers['Set-Cookie'][0]}");
        assertThat(expr.eval(exchange, String.class)).isEqualTo("MyCookie=example; path=/");
    }

    @Test
    public void testAccessingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);
        bfm.put("attribute", "hello");

        assertThat(new Expression("${legacy}").eval(bfm, String.class)).isEqualTo("OpenIG");
        assertThat(new Expression("${number}").eval(bfm, Integer.class)).isEqualTo(42);
        assertThat(new Expression("${readOnly}").eval(bfm, String.class)).isEqualTo("hello");
        assertThat(new Expression("${attribute}").eval(bfm, String.class)).isEqualTo("hello");
        assertThat(new Expression("${missing}").eval(bfm, String.class)).isNull();
    }

    @Test
    public void testSettingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);

        new Expression("${legacy}").set(bfm, "ForgeRock");
        assertThat(bfm.legacy).isEqualTo("ForgeRock");

        new Expression("${number}").set(bfm, 404);
        assertThat(bfm.getNumber()).isEqualTo(404);

        new Expression("${readOnly}").set(bfm, "will-be-ignored");
        assertThat(bfm.getReadOnly()).isEqualTo("hello");

        new Expression("${attribute}").set(bfm, "a-value");
        assertThat(bfm.get("attribute")).isEqualTo("a-value");
    }

    @Test
    public void testUsingIntermediateBean() throws Exception {
        ExternalBean bean = new ExternalBean(new InternalBean("Hello World"));

        assertThat(new Expression("${internal.value}").eval(bean, String.class)).isEqualTo("Hello World");
        new Expression("${internal.value}").set(bean, "ForgeRock OpenIG");
        assertThat(bean.getInternal().getValue()).isEqualTo("ForgeRock OpenIG");
    }

    @Test
    public void testImplicitObjectsReferences() throws Exception {
        assertThat(new Expression("${system['user.home']}").eval(null)).isNotNull();
        assertThat(new Expression("${env['PATH']}").eval(null)).isNotNull();
    }

    @Test
    public void getNullExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Object o = new Expression("${exchange.request.entity.string}").eval(exchange);
        assertThat(o).isEqualTo("");
    }

    @Test
    public void getNullExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        Object o = new Expression("${exchange.request.entity.json}").eval(exchange);
        assertThat(o).isNull();
    }

    @Test
    public void getExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("old mcdonald had a farm");
        Object o = new Expression("${exchange.request.entity.string}").eval(exchange);
        assertThat(o).isEqualTo("old mcdonald had a farm");
    }

    @Test
    public void getExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("{ \"string\" : \"string\", \"int\" : 12345 }");
        Object map = new Expression("${exchange.request.entity.json}").eval(exchange);
        assertThat(map).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) map).containsOnly(entry("string", "string"), entry("int", 12345L));
        Object i = new Expression("${exchange.request.entity.json.int}").eval(exchange);
        assertThat(i).isEqualTo(Long.valueOf(12345));
    }

    @Test
    public void setExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        new Expression("${exchange.request.entity.json}").set(exchange, object(field("k1", "v1"),
                                                                               field("k2", 123)));
        assertThat(exchange.request.getEntity()).isNotNull();
        assertThat(exchange.request.getEntity().getString())
                .isEqualTo("{\"k1\":\"v1\",\"k2\":123}");
    }

    @Test
    public void setExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        new Expression("${exchange.request.entity.string}").set(exchange,
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
