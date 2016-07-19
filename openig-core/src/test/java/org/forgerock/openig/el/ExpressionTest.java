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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
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
        Expression<String> expr = Expression.valueOf("foo${'\\\\'}${a} ${a}${b} foo${'\\\\'}${b}", String.class);
        String o = expr.eval(bindings().bind("a", "bar").bind("b", "bas"));
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
        String o = expr.eval(bindings("a", "foo"));
        assertThat(o).isEqualTo("foobar");
    }

    @Test
    public void requestHeader() throws ExpressionException {
        Request request = new Request();
        request.getHeaders().put("Host", "www.example.com");
        Expression<String> expr = Expression.valueOf("${request.headers['Host'][0]}", String.class);
        String host = expr.eval(bindings("request", request));
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    public void requestURI() throws ExpressionException, java.net.URISyntaxException {
        Request request = new Request();
        request.setUri("http://test.com:123/path/to/resource.html");
        String o = Expression.valueOf("${request.uri.path}", String.class).eval(bindings("request", request));
        assertThat(o).isEqualTo("/path/to/resource.html");
    }

    @Test
    public void setAttribute() throws ExpressionException {
        AttributesContext context = new AttributesContext(new RootContext());
        Bindings bindings = bindings(context, null);
        @SuppressWarnings("rawtypes")
        Expression<Map> expr = Expression.valueOf("${contexts.attributes.attributes.testmap}", Map.class);
        expr.set(bindings, singletonMap("foo", "bar"));
        Expression<String> foo = Expression.valueOf("${contexts.attributes.attributes.testmap.foo}", String.class);
        Expression<String> easyAccess = Expression.valueOf("${attributes.testmap.foo}", String.class);
        assertThat(foo.eval(bindings)).isEqualTo(easyAccess.eval(bindings)).isEqualTo("bar");
    }

    @Test
    public void setSessionAttribute() throws ExpressionException {
        final SessionContext context = new SessionContext(new RootContext(), new SimpleMapSession());
        final Session session = context.asContext(SessionContext.class).getSession();
        final String text = "miniature giant space hamster";
        session.put("Boo", text);

        final Bindings bindings = bindings(context, null);
        final Expression<String> expr = Expression.valueOf("${contexts.session.session.Boo}", String.class);
        final Expression<String> easyAccess = Expression.valueOf("${session.Boo}", String.class);
        assertThat(expr.eval(bindings)).isEqualTo(easyAccess.eval(bindings)).isEqualTo(text);
    }

    @Test
    public void examples() throws Exception {

        // The following are used as examples in the OpenIG documentation, they should all be valid
        Request request = new Request();
        request.setUri("http://wiki.example.com/wordpress/wp-login.php?action=login");
        request.setMethod("POST");
        request.getHeaders().put("host", "wiki.example.com");
        request.getHeaders().put("cookie", "SESSION=value; path=/");

        Response response = new Response();
        response.getHeaders().put("Set-Cookie", "MyCookie=example; path=/");

        Bindings bindings = bindings(null, request, response);

        Expression<Boolean> boolExpr = Expression.valueOf("${request.uri.path == '/wordpress/wp-login.php' "
                        + "and request.form['action'][0] != 'logout'}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${request.uri.path == '/wordpress/wp-login.php' ? true : false}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${request.uri.host == 'wiki.example.com'}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${request.method == 'POST' "
                + "and request.uri.path == '/wordpress/wp-login.php'}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${request.method != 'GET'}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${request.uri.scheme == 'http'}", Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        boolExpr = Expression.valueOf("${not (response.status.code == 302 and "
                + "not empty session.gotoURL)}",
                Boolean.class);
        assertThat(boolExpr.eval(bindings)).isTrue();

        Expression<String> stringExpr = Expression.valueOf("${toString(request.uri)}", String.class);
        assertThat(stringExpr.eval(bindings)).isEqualTo("http://wiki.example.com/wordpress/wp-login.php?action=login");

        stringExpr = Expression.valueOf("${request.headers['host'][0]}", String.class);
        assertThat(stringExpr.eval(bindings)).isEqualTo("wiki.example.com");

        stringExpr = Expression.valueOf("${request.cookies[keyMatch(request.cookies,'^SESS.*')]"
                + "[0].value}", String.class);
        assertThat(stringExpr.eval(bindings)).isNotNull();

        stringExpr = Expression.valueOf("${request.headers['cookie'][0]}", String.class);
        assertThat(stringExpr.eval(bindings)).isNotNull();

        stringExpr = Expression.valueOf(
                "${empty request.headers['cookie'][0] ? 'name=Tesla' : request.headers['cookie'][0]}", String.class);
        assertThat(stringExpr.eval(bindings)).isEqualTo("SESSION=value; path=/");

        stringExpr = Expression.valueOf("${response.headers['Set-Cookie'][0]}", String.class);
        assertThat(stringExpr.eval(bindings)).isEqualTo("MyCookie=example; Path=/");
    }

    @Test
    public void testUsingIntermediateBean() throws Exception {
        ExternalBean bean = new ExternalBean(new InternalBean("Hello World"));

        Bindings bindings = bindings("bean", bean);
        assertThat(Expression.valueOf("${bean.internal.value}", String.class)
                             .eval(bindings))
                .isEqualTo("Hello World");
        Expression.valueOf("${bean.internal.value}", String.class).set(bindings, "ForgeRock OpenIG");
        assertThat(bean.getInternal().getValue()).isEqualTo("ForgeRock OpenIG");
    }

    @Test
    public void testRealBeanProperties() throws Exception {
        InternalBean myBean = new InternalBean("foo");
        Expression<Integer> expr = Expression.valueOf("${bean.bar}", Integer.class);
        Integer o = expr.eval(bindings("bean", myBean));
        assertThat(o).isNull();
    }

    @Test
    public void testImplicitObjectsReferences() throws Exception {
        assertThat(Expression.valueOf("${system['user.home']}", String.class).eval()).isNotNull();
        assertThat(Expression.valueOf("${env['PATH']}", String.class).eval()).isNotNull();
    }

    @Test
    public void getNullRequestEntityAsString() throws Exception {
        String o = Expression.valueOf("${request.entity.string}", String.class)
                             .eval(bindings("request", new Request()));
        assertThat(o).isEqualTo("");
    }

    @Test
    public void getNullRequestEntityAsJson() throws Exception {
        Map<?, ?> o = Expression.valueOf("${request.entity.json}", Map.class)
                                .eval(bindings("request", new Request()));
        assertThat(o).isNull();
    }

    @Test
    public void getRequestEntityAsString() throws Exception {
        Request request = new Request();
        request.setEntity("old mcdonald had a farm");
        String o = Expression.valueOf("${request.entity.string}", String.class)
                             .eval(bindings("request", request));
        assertThat(o).isEqualTo("old mcdonald had a farm");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getRequestEntityAsJson() throws Exception {
        Request request = new Request();
        request.setEntity("{ \"string\" : \"string\", \"int\" : 12345 }");
        Map<String, Object> map = Expression.valueOf("${request.entity.json}", Map.class)
                                  .eval(bindings("request", request));
        assertThat(map).containsOnly(entry("string", "string"), entry("int", 12345));

        Integer i = Expression.valueOf("${request.entity.json.int}", Integer.class)
                              .eval(bindings("request", request));
        assertThat(i).isEqualTo(12345);
    }

    @Test
    public void setRequestEntityAsJson() throws Exception {
        Request request = new Request();
        Expression.valueOf("${request.entity.json}", Map.class)
                  .set(bindings("request", request), object(field("k1", "v1"), field("k2", 123)));
        assertThat(request.getEntity()).isNotNull();
        assertThat(request.getEntity().getString())
                .isEqualTo("{\"k1\":\"v1\",\"k2\":123}");
    }

    @Test
    public void setRequestEntityAsString() throws Exception {
        Request request = new Request();
        Expression.valueOf("${request.entity.string}", String.class)
                  .set(bindings("request", request), "mary mary quite contrary");
        assertThat(request.getEntity()).isNotNull();
        assertThat(request.getEntity().getString()).isEqualTo("mary mary quite contrary");
    }

    @Test
    public void testExpressionEvaluation() throws Exception {
        Expression<String> username = Expression.valueOf("realm${'\\\\'}${request.headers['username'][0]}",
                String.class);
        Expression<String> password = Expression.valueOf("${request.headers['password'][0]}", String.class);
        Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://test.com:123/path/to/resource.html");
        request.getHeaders().add("username", "Myname");
        request.getHeaders().add("password", "Mypass");

        Bindings bindings = bindings("request", request);
        String user = username.eval(bindings);
        String pass = password.eval(bindings);

        assertThat(user).isEqualTo("realm\\Myname");
        assertThat(pass).isEqualTo("Mypass");
    }

    @Test
    public void shouldCallStringMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${a.concat(' World')}", String.class);
        assertThat(expression.eval(bindings("a", "Hello")))
                .isEqualTo("Hello World");
    }

    @Test
    public void shouldChainMethodCalls() throws Exception {
        Expression<String> expression = Expression.valueOf("${a.concat(' World').concat(' !')}", String.class);
        assertThat(expression.eval(bindings("a", "Hello")))
                .isEqualTo("Hello World !");
    }

    @Test
    public void shouldCallBeanMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${bean.concat(' World')}", String.class);
        assertThat(expression.eval(bindings("bean", new ConcatBean("Hello"))))
                .isEqualTo("Hello World");
    }

    @Test
    public void shouldCallIntegerMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${integer.toString()}", String.class);
        assertThat(expression.eval(bindings("integer", 42)))
                .isEqualTo("42");
    }

    @Test
    public void shouldCallStaticMethod() throws Exception {
        // I agree this one is a bit weird because we need an instance to base our computation onto
        Expression<Integer> expression = Expression.valueOf("${integer.valueOf(42)}", Integer.class);
        assertThat(expression.eval(bindings("integer", 37)))
                .isEqualTo(42);
    }

    @Test
    public void shouldReturnNullWhenTryingToCallNonDefinedMethod() throws Exception {
        Expression<String> expression = Expression.valueOf("${item.thereIsNoSuchMethod()}", String.class);
        assertThat(expression.eval(bindings("item", "Hello")))
                .isNull();
    }

    @Test
    public void shouldReturnHeapObjects() throws Exception {
        HeapImpl heap = new HeapImpl(Name.of("ExpressionTest"));
        heap.put("foo", "bar");
        Expression<String> expression = Expression.valueOf("${heap['foo']}", String.class);
        assertThat(expression.eval(bindings("heap", heap))).isEqualTo("bar");
    }

    @Test
    public void shouldReturnNullForAbsentHeapObject() throws Exception {
        HeapImpl heap = new HeapImpl(Name.of("ExpressionTest"));
        Expression<String> expression = Expression.valueOf("${heap['foo']}", String.class);
        assertThat(expression.eval(bindings("heap", heap))).isNull();
    }

    @Test
    public void shouldUseInitialBindingsWhenEvaluating() throws Exception {
        Bindings initialBindings = bindings().bind("a", 1);
        Expression<Long> expression = Expression.valueOf("${a + b}", Long.class, initialBindings);

        Bindings evaluationBindings = bindings().bind("b", 2);
        Long eval = expression.eval(evaluationBindings);

        assertThat(eval).isEqualTo(3L);
        assertThat(initialBindings.asMap()).containsExactly(entry("a", 1));
        assertThat(evaluationBindings.asMap()).containsExactly(entry("b", 2));
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

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }
}
