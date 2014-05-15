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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2013 ForgeRock AS.
 */

package org.forgerock.openig.el;

import java.net.URI;
import java.util.HashMap;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.openig.http.Response;
import org.testng.annotations.Test;

import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;

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
        exchange.request.headers.putSingle("Host", "www.example.com");
        Expression expr = new Expression("${exchange.request.headers['Host'][0]}");
        String host = expr.eval(exchange, String.class);
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    public void exchangeRequestURI() throws ExpressionException, java.net.URISyntaxException {
        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test.com:123/path/to/resource.html");
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
        request.uri = new URI("http://wiki.example.com/wordpress/wp-login.php?action=login");
        request.method = "POST";
        request.headers.putSingle("host", "wiki.example.com");
        request.headers.putSingle("cookie", "SESSION=value; path=/");

        Response response = new Response();
        response.headers.putSingle("Set-Cookie", "MyCookie=example; path=/");

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
}
