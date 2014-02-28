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
package org.forgerock.openig.filter;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Collections;

import javax.script.ScriptException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.Test;

/**
 * Tests the Groovy scripting filter.
 */
@SuppressWarnings("javadoc")
public class GroovyScriptFilterTest {

    @Test
    public void testNextHandlerCanBeInvoked() throws Exception {
        final GroovyScriptFilter filter = new GroovyScriptFilter("next.handle(exchange)");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verify(handler).handle(exchange);
    }

    @Test
    public void testNextHandlerCanThrowHandlerException() throws Exception {
        final GroovyScriptFilter filter = new GroovyScriptFilter("next.handle(exchange)");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        final HandlerException expected = new HandlerException();
        doThrow(expected).when(handler).handle(exchange);
        try {
            filter.filter(exchange, handler);
            fail();
        } catch (final HandlerException e) {
            assertThat(e).isSameAs(expected);
        }
    }

    @Test
    public void testNextHandlerPreAndPostConditions() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.response == null",
                "next.handle(exchange)",
                "assert exchange.response != null");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Response expectedResponse = new Response();
        final Handler handler = mock(Handler.class);
        returnResponse(expectedResponse).when(handler).handle(exchange);
        filter.filter(exchange, handler);
        verify(handler).handle(exchange);
        assertThat(exchange.response).isSameAs(expectedResponse);
    }

    private Stubber returnResponse(final Response response) {
        return doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                final Object[] args = invocation.getArguments();
                ((Exchange) args[0]).response = response;
                return null;
            }
        });
    }

    @Test
    public void testBindingsArePresent() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                        "assert exchange != null",
                        "assert exchange.request != null",
                        "assert exchange.response == null",
                        "assert logger != null",
                        "assert next != null");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verifyZeroInteractions(handler);
    }

    @Test
    public void testConstructFromFile() throws Exception {
        final HeapImpl heap = new HeapImpl();
        heap.put("TemporaryStorage", new TemporaryStorage());
        final GroovyScriptFilter filter =
                (GroovyScriptFilter) new GroovyScriptFilter.Heaplet().create("test", new JsonValue(
                        Collections.singletonMap("scriptFile", "src/test/resources/test.groovy")),
                        heap);
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verifyZeroInteractions(handler);
        assertThat(exchange.response).isNotNull();
    }

    @Test
    public void testConstructFromString() throws Exception {
        final HeapImpl heap = new HeapImpl();
        heap.put("TemporaryStorage", new TemporaryStorage());
        final String script =
                "import org.forgerock.openig.http.Response;exchange.response = new Response()";
        final GroovyScriptFilter filter =
                (GroovyScriptFilter) new GroovyScriptFilter.Heaplet().create("test", new JsonValue(
                        Collections.singletonMap("script", script)), heap);
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verifyZeroInteractions(handler);
        assertThat(exchange.response).isNotNull();
    }

    @Test
    public void testThrowHandlerException() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "import org.forgerock.openig.handler.HandlerException",
                "throw new HandlerException(\"test\")");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        try {
            filter.filter(exchange, handler);
            fail();
        } catch (final HandlerException e) {
            assertThat(e.getMessage()).isEqualTo("test");
        }
    }

    @Test
    public void testSetResponse() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "import org.forgerock.openig.http.Response",
                "exchange.response = new Response()",
                "exchange.response.status = 404");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(exchange.response).isNotNull();
        assertThat(exchange.response.status).isEqualTo(404);
    }

    @Test
    public void testLogging() throws Exception {
        final GroovyScriptFilter filter = new GroovyScriptFilter("logger.error('test')");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.logger = mock(Logger.class);
        when(filter.logger.getTimer()).thenReturn(new LogTimer(filter.logger));
        filter.filter(exchange, handler);
        verify(filter.logger).error("test");
    }

    @Test(expectedExceptions = ScriptException.class)
    public void testCompilationFailure() throws Exception {
        new GroovyScriptFilter("import does.not.Exist");
    }

    @Test(expectedExceptions = ScriptException.class)
    public void testRunTimeFailure() throws Throwable {
        final GroovyScriptFilter filter = new GroovyScriptFilter("dummy + 1");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        try {
            filter.filter(exchange, handler);
            fail();
        } catch (final HandlerException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testAssignment() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "exchange.test = false",
                "next.handle(exchange)",
                "exchange.test = exchange.response.status == 302");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        Response response = new Response();
        response.status = 302;
        returnResponse(response).when(handler).handle(exchange);
        filter.filter(exchange, handler);
        assertThat(exchange.get("test")).isEqualTo(true);
    }

    @Test
    public void testRequestForm() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.request.form.username[0] == 'test'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test?username=test");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestCookies() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.request.cookies.username[0].value == 'test'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.headers.add("Cookie", "username=test;Path=/");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestURI() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.request.uri.scheme == 'http'",
                "assert exchange.request.uri.host == 'example.com'",
                "assert exchange.request.uri.port == 8080",
                "assert exchange.request.uri.path == '/users'",
                "assert exchange.request.uri.query == 'action=create'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://example.com:8080/users?action=create");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestHeaders() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.request.headers.Username[0] == 'test'",
                "exchange.request.headers.Test = [ 'test' ]",
                "assert exchange.request.headers.remove('Username')");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.headers.add("Username", "test");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(exchange.request.headers.get("Test")).containsOnly("test");
        assertThat(exchange.request.headers.get("Username")).isNull();
    }

    @Test
    public void testSession() throws Exception {
        // @formatter:off
        final GroovyScriptFilter filter = new GroovyScriptFilter(
                "assert exchange.session.inKey == 'inValue'",
                "exchange.session.outKey = 'outValue'",
                "assert exchange.session.remove('inKey')");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.session = mock(Session.class);
        when(exchange.session.get("inKey")).thenReturn("inValue");
        when(exchange.session.remove("inKey")).thenReturn(true);
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verify(exchange.session).get("inKey");
        verify(exchange.session).put("outKey", "outValue");
        verify(exchange.session).remove("inKey");
        verifyNoMoreInteractions(exchange.session);
    }

    // TODO: entity interaction.
}
