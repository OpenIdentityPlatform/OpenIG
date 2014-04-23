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

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.get;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.uri;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.handler.ScriptableHandler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.io.ByteArrayBranchingStream;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.script.Script;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.Test;

import com.xebialabs.restito.server.StubServer;

/**
 * Tests Groovy integration for the scriptable filter.
 */
@SuppressWarnings("javadoc")
public class GroovyScriptableFilterTest {

    private static final String XML_CONTENT =
            "<root><a a1='one'><b>3 &lt; 5</b><c a2='two'>blah</c></a></root>";
    private static final String JSON_CONTENT = "{\"person\":{" + "\"firstName\":\"Tim\","
            + "\"lastName\":\"Yates\"," + "\"address\":{\"city\":\"Manchester\","
            + "\"country\":\"UK\",\"zip\":\"M1 2AB\"}}}";

    @Test
    public void testAssignment() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.test = false",
                "next.handle(exchange)",
                "exchange.test = exchange.response.status == 302");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        final Response response = new Response();
        response.status = 302;
        returnResponse(response).when(handler).handle(exchange);
        filter.filter(exchange, handler);
        assertThat(exchange.get("test")).isEqualTo(true);
    }

    @Test
    public void testBindingsArePresent() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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

    @Test(expectedExceptions = ScriptException.class, enabled = false)
    public void testCompilationFailure() throws Exception {
        newGroovyFilter("import does.not.Exist");
    }

    @Test
    public void testConstructFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("TestFileBasedScript.groovy");
        final ScriptableFilter filter =
                (ScriptableFilter) new ScriptableFilter.Heaplet().create("test", new JsonValue(
                        config), getHeap());

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verifyZeroInteractions(handler);
        assertThat(exchange.response).isNotNull();
    }

    @Test
    public void testDispatchFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("DispatchHandler.groovy");
        final ScriptableHandler handler =
                (ScriptableHandler) new ScriptableHandler.Heaplet().create(
                        "test", new JsonValue(config), getHeap());

        // Try with valid credentials
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test/login");
        exchange.request.headers.add("Username", "bjensen");
        exchange.request.headers.add("Password", "hifalutin");
        exchange.response = new Response();
        handler.handle(exchange);
        assertThat(exchange.response.status).isEqualTo(200);

        // Try with invalid credentials
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test/login");
        exchange.request.headers.add("Username", "bob");
        exchange.request.headers.add("Password", "dobbs");
        exchange.response = new Response();
        handler.handle(exchange);
        assertThat(exchange.response.status).isEqualTo(403);

        // Try with different path
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test/index.html");
        exchange.response = new Response();
        handler.handle(exchange);
        assertThat(exchange.response.status).isEqualTo(401);
    }

    @Test
    public void testBasicAuthFilterFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("BasicAuthFilter.groovy");
        final ScriptableFilter filter =
                (ScriptableFilter) new ScriptableFilter.Heaplet().create("test", new JsonValue(
                        config), getHeap());

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://www.example.com/");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        // base64-encode "bjensen:hifalutin" -> "YmplbnNlbjpoaWZhbHV0aW4="
        assertThat(exchange.request.headers.get("Authorization").toString())
                .isEqualTo("[Basic YmplbnNlbjpoaWZhbHV0aW4=]");
        assertThat(exchange.request.uri.getScheme()).isEqualTo("https");
    }

    @Test
    public void testConstructFromString() throws Exception {
        final String script =
                "import org.forgerock.openig.http.Response;exchange.response = new Response()";
        final Map<String, Object> config = new HashMap<String, Object>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("source", script);
        final ScriptableFilter filter =
                (ScriptableFilter) new ScriptableFilter.Heaplet().create("test", new JsonValue(
                        config), getHeap());

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verifyZeroInteractions(handler);
        assertThat(exchange.response).isNotNull();
    }

    @Test
    public void testGlobalsPersistedBetweenInvocations() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert globals.x == null",
                "globals.x = 'value'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        try {
            filter.filter(exchange, handler);
            fail("Second iteration succeeded unexpectedly");
        } catch (final AssertionError e) {
            // Expected.
        }
    }

    @Test
    public void testHttpClient() throws Exception {
        // Create mock HTTP server.
        final StubServer server = new StubServer().run();
        whenHttp(server).match(get("/example")).then(status(HttpStatus.OK_200),
                stringContent(JSON_CONTENT));
        try {
            final int port = server.getPort();
            // @formatter:off
            final ScriptableFilter filter = newGroovyFilter(
                    "import org.forgerock.openig.http.*",
                    "Request request = new Request()",
                    "request.method = 'GET'",
                    "request.uri = new URI('http://0.0.0.0:" + port + "/example')",
                    "exchange.response = http.execute(request)");
            filter.setHttpClient(new HttpClient(new TemporaryStorage()));

            // @formatter:on
            final Exchange exchange = new Exchange();
            exchange.request = new Request();
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);

            verifyHttp(server).once(method(Method.GET), uri("/example"));
            assertThat(exchange.response.status).isEqualTo(200);
            assertThat(s(exchange.response.entity)).isEqualTo(JSON_CONTENT);
        } finally {
            server.stop();
        }
    }

    @Test(enabled = true)
    public void testLdapClient() throws Exception {
        // Create mock LDAP server with a single user.
        // @formatter:off
        final MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(
                "dn:",
                "objectClass: top",
                "objectClass: extensibleObject",
                "",
                "dn: dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: com",
                "",
                "dn: dc=example,dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: example",
                "",
                "dn: ou=people,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: people",
                "",
                "dn: uid=bjensen,ou=people,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "cn: Barbara",
                "sn: Jensen",
                "uid: bjensen",
                "description: test user",
                "userPassword: password"));
        // @formatter:on
        final LDAPListener listener =
                new LDAPListener(0, Connections
                        .<LDAPClientContext> newServerConnectionFactory(backend));
        final int port = listener.getPort();
        try {
            // @formatter:off
            final ScriptableFilter filter = newGroovyFilter(
                    "import org.forgerock.opendj.ldap.*",
                    "import org.forgerock.openig.http.Response",
                    "",
                    "username = exchange.request.headers.Username[0]",
                    "password = exchange.request.headers.Password[0]",
                    "",
                    "exchange.response = new Response()",
                    "",
                    "client = ldap.connect('0.0.0.0'," + port + ")",
                    "try {",
                    "  user = client.searchSingleEntry('ou=people,dc=example,dc=com',",
                    "                                  ldap.scope.sub,",
                    "                                  ldap.filter('(uid=%s)', username))",
                    "  client.bind(user.name.toString(), password.toCharArray())",
                    "  exchange.response.status = 200",
                    // Attributes as MetaClass properties
                    "  exchange.response.reason = user.description.parse().asString()",
                    "  user.description = 'some value'",
                    "  assert user.description.parse().asString() == 'some value'",
                    "  user.description = ['one', 'two']",
                    "  assert user.description.parse().asSetOfString() == ['one', 'two'] as Set",
                    "  user.description += 'three'",
                    "  assert user.description.parse().asSetOfString() == ['one', 'two', 'three'] as Set",
                    "} catch (AuthenticationException e) {",
                    "  exchange.response.status = 403",
                    "  exchange.response.reason = e.message",
                    "} catch (Exception e) {",
                    "  exchange.response.status = 500",
                    "  exchange.response.reason = e.message",
                    "} finally {",
                    "  client.close()",
                    "}");
            // @formatter:on

            // Authenticate using correct password.
            final Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.headers.add("Username", "bjensen");
            exchange.request.headers.add("Password", "password");
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);
            assertThat(exchange.response.status).as(exchange.response.reason).isEqualTo(200);
            assertThat(exchange.response.reason).isEqualTo("test user");

            // Authenticate using wrong password.
            exchange.request = new Request();
            exchange.request.headers.add("Username", "bjensen");
            exchange.request.headers.add("Password", "wrong");
            filter.filter(exchange, handler);
            assertThat(exchange.response.status).isEqualTo(403);
            assertThat(exchange.response.reason).isNotNull();
        } finally {
            listener.close();
        }
    }

    @Test(enabled = true)
    public void testLdapAuthFromFile() throws Exception {
        // Create mock LDAP server with a single user.
        // @formatter:off
        final MemoryBackend backend = new MemoryBackend(new LDIFEntryReader(
                "dn:",
                "objectClass: top",
                "objectClass: extensibleObject",
                "",
                "dn: dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: com",
                "",
                "dn: dc=example,dc=com",
                "objectClass: domain",
                "objectClass: top",
                "dc: example",
                "",
                "dn: ou=people,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: people",
                "",
                "dn: uid=bjensen,ou=people,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "cn: Barbara",
                "sn: Jensen",
                "uid: bjensen",
                "description: test user",
                "userPassword: hifalutin"));
        // @formatter:on
        final LDAPListener listener =
                new LDAPListener(0, Connections
                        .<LDAPClientContext> newServerConnectionFactory(backend));
        final int port = listener.getPort();
        try {
            final Map<String, Object> config = newFileConfig("LdapAuthFilter.groovy");
            final ScriptableFilter filter =
                    (ScriptableFilter) new ScriptableFilter.Heaplet()
                            .create("test", new JsonValue(config), getHeap());

            // Authenticate using correct password.
            final Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.uri = new URI("http://test?username=bjensen&password=hifalutin");
            // FixMe: Passing the LDAP host and port as headers is wrong.
            exchange.put("ldapHost", "localhost");
            exchange.put("ldapPort", "" + port);
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);
            assertThat(exchange.request.headers.get("Ldap-User-Dn").toString())
                    .isEqualTo("[uid=bjensen,ou=people,dc=example,dc=com]");

            // Authenticate using wrong password.
            exchange.request = new Request();
            exchange.request.uri = new URI("http://test?username=bjensen&password=wrong");
            // FixMe: Passing the LDAP host and port as headers is wrong.
            exchange.request.headers.add("LdapHost", "0.0.0.0");
            exchange.request.headers.add("LdapPort", "" + port);
            filter.filter(exchange, handler);
            assertThat(exchange.response.status).isEqualTo(403);
            assertThat(exchange.response.reason).isNotNull();
        } finally {
            listener.close();
        }
    }

    @Test
    public void testLogging() throws Exception {
        final ScriptableFilter filter = newGroovyFilter("logger.error('test')");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.logger = mock(Logger.class);
        when(filter.logger.getTimer()).thenReturn(new LogTimer(filter.logger));
        filter.filter(exchange, handler);
        verify(filter.logger).error("test");
    }

    @Test
    public void testNextHandlerCanBeInvoked() throws Exception {
        final ScriptableFilter filter = newGroovyFilter("next.handle(exchange)");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        verify(handler).handle(exchange);
    }

    @Test
    public void testNextHandlerCanThrowHandlerException() throws Exception {
        final ScriptableFilter filter = newGroovyFilter("next.handle(exchange)");
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
        final ScriptableFilter filter = newGroovyFilter(
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

    @Test(enabled = false)
    public void testReadJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.jsonIn.person.firstName == 'Tim'",
                "assert exchange.request.jsonIn.person.lastName == 'Yates'",
                "assert exchange.request.jsonIn.person.address.country == 'UK'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.entity = new ByteArrayBranchingStream(JSON_CONTENT.getBytes("UTF-8"));
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test(enabled = false)
    public void testReadXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.xmlIn.root.a"); // TODO
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.entity = new ByteArrayBranchingStream(XML_CONTENT.getBytes("UTF-8"));
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestCookies() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.cookies.username[0].value == 'test'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.headers.add("Cookie", "username=test;Path=/");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestForm() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.form.username[0] == 'test'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://test?username=test");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test
    public void testRequestHeaders() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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
    public void testRequestURI() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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
    public void testResponseEntity() throws Exception {
        /*
         * FIXME: this usage is horrible! Better encapsulation of the HTTP
         * message fields would allow for overloading of the entity setter as
         * well as other setters, e.g. setEntity(String),
         * setEntityAsJson(Object), etc.
         */

        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.openig.http.*",
                "import org.forgerock.openig.io.*",
                "exchange.response = new Response()",
                "exchange.response.status = 200",
                "exchange.response.entity = new ByteArrayBranchingStream('hello world'.getBytes())");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);

        assertThat(exchange.response.status).isEqualTo(200);
        assertThat(s(exchange.response.entity)).isEqualTo("hello world");
    }

    @Test(expectedExceptions = ScriptException.class)
    public void testRunTimeFailure() throws Throwable {
        final ScriptableFilter filter = newGroovyFilter("dummy + 1");
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
    public void testSession() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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

    @Test
    public void testSetResponse() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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
    public void testThrowHandlerException() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
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

    @Test(enabled = false)
    public void testWriteJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.request.jsonOut.person {",
                    "firstName 'Tim'",
                    "lastName 'Yates'",
                    "address {",
                        "city: 'Manchester'",
                            "country: 'UK'",
                            "zip: 'M1 2AB'",
                            "}",
                        "}");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(s(exchange.request.entity)).isEqualTo(JSON_CONTENT);
    }

    @Test(enabled = false)
    public void testWriteXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.request.xmlOut.root {",
                    "a( a1:'one' ) {",
                        "b { mkp.yield( '3 < 5' ) }",
                            "c( a2:'two', 'blah' )",
                            "}",
                        "}");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(s(exchange.request.entity)).isEqualTo(XML_CONTENT);
    }

    private HeapImpl getHeap() {
        final HeapImpl heap = new HeapImpl();
        heap.put("TemporaryStorage", new TemporaryStorage());
        heap.put("Environment", getEnvironment());
        return heap;
    }

    private Environment getEnvironment() {
        return Environment.forStandaloneApp("src/test/resources");
    }

    private Map<String, Object> newFileConfig(final String groovyClass) {
        final Map<String, Object> config = new HashMap<String, Object>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);
        return config;
    }

    private ScriptableFilter newGroovyFilter(final String... sourceLines) throws ScriptException {
        final Environment environment = getEnvironment();
        final Script script = Script.fromSource(environment, Script.GROOVY_MIME_TYPE, sourceLines);
        return new ScriptableFilter(script);
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

    private String s(final InputStream is) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

}
