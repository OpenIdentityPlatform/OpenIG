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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;

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
        Expression<Boolean> expr = Expression.valueOf("${1==1}", Boolean.class);
        Boolean o = expr.eval();
        assertThat(o).isEqualTo(true);
    }

    @Test
    public void boolBadSyntaxIsNull() throws ExpressionException {
        Expression<Boolean> expr = Expression.valueOf("not a boolean", Boolean.class);
        Boolean o = expr.eval();
        assertThat(o).isNull();
    }

    @Test
    public void integerBadSyntaxIsNull() throws ExpressionException {
        Expression<Integer> expr = Expression.valueOf("not an integer", Integer.class);
        Integer o = expr.eval();
        assertThat(o).isNull();
    }

    @Test
    public void nullStringIsNull() throws ExpressionException {
        Expression<String> expr = Expression.valueOf("${null}", String.class);
        String o = expr.eval();
        assertThat(o).isNull();
    }

    @Test
    public void empty() throws ExpressionException {
        Expression<String> expr = Expression.valueOf("string-literal", String.class);
        String o = expr.eval();
        assertThat(o).isEqualTo("string-literal");
    }

    @Test
    public void emptyString() throws ExpressionException {
        Expression<String> expr = Expression.valueOf("", String.class);
        String o = expr.eval();
        assertThat(o).isEqualTo("");
    }

    @Test
    public void backslash() throws ExpressionException {
        HashMap<String, String> scope = new HashMap<>();
        scope.put("a", "bar");
        scope.put("b", "bas");
        Expression<String> expr = Expression.valueOf("foo${'\\\\'}${a} ${a}${b} foo${'\\\\'}${b}", String.class);
        String o = expr.eval(scope);
        assertThat(o).isEqualTo("foo\\bar barbas foo\\bas");
    }

    @Test(dataProvider = "expressions")
    public void toStringTest(final String value) throws ExpressionException {
        final Expression<String> expA = Expression.valueOf(value, String.class);
        final Expression<String> expB = Expression.valueOf(expA.toString(), String.class);
        assertThat(expA.toString()).isEqualTo(expB.toString());
    }

    @Test
    public void scope() throws ExpressionException {
        Expression<String> expr = Expression.valueOf("${a}bar", String.class);
        String o = expr.eval(singletonMap("a", "foo"));
        assertThat(o).isEqualTo("foobar");
    }

    @Test
    public void exchangeRequestHeader() throws ExpressionException {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().getHeaders().putSingle("Host", "www.example.com");
        Expression<String> expr = Expression.valueOf("${exchange.request.headers['Host'][0]}", String.class);
        String host = expr.eval(exchange);
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    public void exchangeRequestURI() throws ExpressionException, java.net.URISyntaxException {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setUri("http://test.com:123/path/to/resource.html");
        String o = Expression.valueOf("${exchange.request.uri.path}", String.class).eval(exchange);
        assertThat(o).isEqualTo("/path/to/resource.html");
    }

    @Test
    public void exchangeSetAttribute() throws ExpressionException {
        Exchange exchange = new Exchange();
        @SuppressWarnings("rawtypes")
        Expression<Map> expr = Expression.valueOf("${exchange.testmap}", Map.class);
        expr.set(exchange, singletonMap("foo", "bar"));
        Expression<String> foo = Expression.valueOf("${exchange.testmap.foo}", String.class);
        assertThat(foo.eval(exchange)).isEqualTo("bar");
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
        exchange.setRequest(request);
        exchange.setResponse(response);

        Expression<Boolean> boolExpr = Expression.valueOf("${exchange.request.uri.path == '/wordpress/wp-login.php' "
                        + "and exchange.request.form['action'][0] != 'logout'}", Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        boolExpr = Expression.valueOf("${exchange.request.uri.host == 'wiki.example.com'}", Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        boolExpr = Expression.valueOf("${exchange.request.method == 'POST' "
                + "and exchange.request.uri.path == '/wordpress/wp-login.php'}", Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        boolExpr = Expression.valueOf("${exchange.request.method != 'GET'}", Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        boolExpr = Expression.valueOf("${exchange.request.uri.scheme == 'http'}", Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        boolExpr = Expression.valueOf("${not (exchange.response.status.code == 302 and "
                + "not empty exchange.session.gotoURL)}",
                Boolean.class);
        assertThat(boolExpr.eval(exchange)).isTrue();

        Expression<String> stringExpr = Expression.valueOf("${toString(exchange.request.uri)}", String.class);
        assertThat(stringExpr.eval(exchange)).isEqualTo("http://wiki.example.com/wordpress/wp-login.php?action=login");

        stringExpr = Expression.valueOf("${exchange.request.headers['host'][0]}", String.class);
        assertThat(stringExpr.eval(exchange)).isEqualTo("wiki.example.com");

        stringExpr = Expression.valueOf("${exchange.request.cookies[keyMatch(exchange.request.cookies,'^SESS.*')]"
                + "[0].value}", String.class);
        assertThat(stringExpr.eval(exchange)).isNotNull();

        stringExpr = Expression.valueOf("${exchange.request.headers['cookie'][0]}", String.class);
        assertThat(stringExpr.eval(exchange)).isNotNull();

        stringExpr = Expression.valueOf("${exchange.response.headers['Set-Cookie'][0]}", String.class);
        assertThat(stringExpr.eval(exchange)).isEqualTo("MyCookie=example; path=/");
    }

    @Test
    public void testAccessingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);
        bfm.put("attribute", "hello");

        assertThat(Expression.valueOf("${legacy}", String.class).eval(bfm)).isEqualTo("OpenIG");
        assertThat(Expression.valueOf("${number}", Integer.class).eval(bfm)).isEqualTo(42);
        assertThat(Expression.valueOf("${readOnly}", String.class).eval(bfm)).isEqualTo("hello");
        assertThat(Expression.valueOf("${attribute}", String.class).eval(bfm)).isEqualTo("hello");
        assertThat(Expression.valueOf("${missing}", Integer.class).eval(bfm)).isNull();
    }

    @Test
    public void testSettingBeanProperties() throws Exception {
        BeanFieldMap bfm = new BeanFieldMap("hello");
        bfm.legacy = "OpenIG";
        bfm.setNumber(42);

        Expression.valueOf("${legacy}", String.class).set(bfm, "ForgeRock");
        assertThat(bfm.legacy).isEqualTo("ForgeRock");

        Expression.valueOf("${number}", Integer.class).set(bfm, 404);
        assertThat(bfm.getNumber()).isEqualTo(404);

        Expression.valueOf("${readOnly}", String.class).set(bfm, "will-be-ignored");
        assertThat(bfm.getReadOnly()).isEqualTo("hello");

        Expression.valueOf("${attribute}", String.class).set(bfm, "a-value");
        assertThat(bfm.get("attribute")).isEqualTo("a-value");
    }

    @Test
    public void testUsingIntermediateBean() throws Exception {
        ExternalBean bean = new ExternalBean(new InternalBean("Hello World"));

        assertThat(Expression.valueOf("${internal.value}", String.class)
                .eval(bean))
                .isEqualTo("Hello World");
        Expression.valueOf("${internal.value}", String.class).set(bean, "ForgeRock OpenIG");
        assertThat(bean.getInternal().getValue()).isEqualTo("ForgeRock OpenIG");
    }

    @Test
    public void testRealBeanProperties() throws Exception {
        InternalBean myBean = new InternalBean("foo");
        Expression<Integer> expr = Expression.valueOf("${bar}", Integer.class);
        Integer o = expr.eval(myBean);
        assertThat(o).isNull();
    }

    @Test
    public void testImplicitObjectsReferences() throws Exception {
        assertThat(Expression.valueOf("${system['user.home']}", String.class).eval()).isNotNull();
        assertThat(Expression.valueOf("${env['PATH']}", String.class).eval()).isNotNull();
    }

    @Test
    public void getNullExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        String o = Expression.valueOf("${exchange.request.entity.string}", String.class).eval(exchange);
        assertThat(o).isEqualTo("");
    }

    @Test
    public void getNullExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        Map<?, ?> o = Expression.valueOf("${exchange.request.entity.json}", Map.class).eval(exchange);
        assertThat(o).isNull();
    }

    @Test
    public void getExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setEntity("old mcdonald had a farm");
        String o = Expression.valueOf("${exchange.request.entity.string}", String.class).eval(exchange);
        assertThat(o).isEqualTo("old mcdonald had a farm");
    }

    @Test
    public void getExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setEntity("{ \"string\" : \"string\", \"int\" : 12345 }");
        Map<?, ?> map = Expression.valueOf("${exchange.request.entity.json}", Map.class).eval(exchange);
        assertThat((Map<?, ?>) map).containsOnly(entry("string", "string"), entry("int", 12345));

        Integer i = Expression.valueOf("${exchange.request.entity.json.int}", Integer.class)
                .eval(exchange);
        assertThat(i).isEqualTo(12345);
    }

    @Test
    public void setExchangeRequestEntityAsJson() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        Expression.valueOf("${exchange.request.entity.json}", Map.class).set(exchange, object(field("k1", "v1"),
                                                                                              field("k2", 123)));
        assertThat(exchange.getRequest().getEntity()).isNotNull();
        assertThat(exchange.getRequest().getEntity().getString())
                .isEqualTo("{\"k1\":\"v1\",\"k2\":123}");
    }

    @Test
    public void setExchangeRequestEntityAsString() throws Exception {
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        Expression.valueOf("${exchange.request.entity.string}", String.class).set(exchange,
                "mary mary quite contrary");
        assertThat(exchange.getRequest().getEntity()).isNotNull();
        assertThat(exchange.getRequest().getEntity().getString()).isEqualTo("mary mary quite contrary");
    }

    @Test
    public void testExpressionEvaluation() throws Exception {
        Expression<String> username = Expression.valueOf("realm${'\\\\'}${exchange.request.headers['username'][0]}",
                String.class);
        Expression<String> password = Expression.valueOf("${exchange.request.headers['password'][0]}", String.class);
        Exchange exchange = new Exchange();
        exchange.setRequest(new Request());
        exchange.getRequest().setMethod("GET");
        exchange.getRequest().setUri("http://test.com:123/path/to/resource.html");
        exchange.getRequest().getHeaders().add("username", "Myname");
        exchange.getRequest().getHeaders().add("password", "Mypass");

        String user = username.eval(exchange);
        String pass = password.eval(exchange);

        assertThat(user).isEqualTo("realm\\Myname");
        assertThat(pass).isEqualTo("Mypass");
    }

    @Test
    public void shouldCallStringMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${a.concat(' World')}", String.class);
        assertThat(expression.eval(singletonMap("a", "Hello")))
                .isEqualTo("Hello World");
    }

    @Test
    public void shouldChainMethodCalls() throws Exception {
        Expression<String> expression = Expression.valueOf("${a.concat(' World').concat(' !')}", String.class);
        assertThat(expression.eval(singletonMap("a", "Hello")))
                .isEqualTo("Hello World !");
    }

    @Test
    public void shouldCallBeanMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${bean.concat(' World')}", String.class);
        assertThat(expression.eval(singletonMap("bean", new ConcatBean("Hello"))))
                .isEqualTo("Hello World");
    }

    @Test
    public void shouldCallIntegerMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${integer.toString()}", String.class);
        assertThat(expression.eval(singletonMap("integer", 42)))
                .isEqualTo("42");
    }

    @Test
    public void shouldCallStaticMethod() throws Exception {
        // I agree this one is a bit weird because we need an instance to base our computation onto
        Expression<Integer> expression = Expression.valueOf("${integer.valueOf(42)}", Integer.class);
        assertThat(expression.eval(singletonMap("integer", 37)))
                .isEqualTo(42);
    }

    @Test
    public void shouldReturnNullWhenTryingToCallNonDefinedMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${item.thereIsNoSuchMethod()}", String.class);
        assertThat(expression.eval(singletonMap("item", "Hello")))
                .isNull();
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

    private static class ConcatBean {
        private String value;

        private ConcatBean(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @SuppressWarnings("unused")
        public void setValue(final String value) {
            this.value = value;
        }

        public String concat(String append) {
            return this.value + append;
        }
    }
}
