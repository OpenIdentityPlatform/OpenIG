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

import static com.xebialabs.restito.builder.stub.StubHttp.*;
import static com.xebialabs.restito.builder.verify.VerifyHttp.*;
import static com.xebialabs.restito.semantics.Action.*;
import static com.xebialabs.restito.semantics.Condition.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Fail.fail;
import static org.forgerock.openig.config.Environment.*;
import static org.forgerock.openig.http.HttpClient.*;
import static org.forgerock.openig.io.TemporaryStorage.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.script.ScriptException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.handler.ScriptableHandler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Headers;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.openig.script.Script;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.Test;

import com.xebialabs.restito.semantics.Condition;
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
        response.setStatus(302);
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
        exchange.request.setUri(new URI("http://test/login"));
        exchange.request.getHeaders().add("Username", "bjensen");
        exchange.request.getHeaders().add("Password", "hifalutin");
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(200);

        // Try with invalid credentials
        exchange.request = new Request();
        exchange.request.setUri(new URI("http://test/login"));
        exchange.request.getHeaders().add("Username", "bob");
        exchange.request.getHeaders().add("Password", "dobbs");
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(403);

        // Try with different path
        exchange.request = new Request();
        exchange.request.setUri(new URI("http://test/index.html"));
        handler.handle(exchange);
        assertThat(exchange.response.getStatus()).isEqualTo(401);
    }

    @Test
    public void testBasicAuthFilterFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("BasicAuthFilter.groovy");
        final ScriptableFilter filter =
                (ScriptableFilter) new ScriptableFilter.Heaplet().create("test", new JsonValue(
                        config), getHeap());

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setUri(new URI("http://www.example.com/"));
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        // base64-encode "bjensen:hifalutin" -> "YmplbnNlbjpoaWZhbHV0aW4="
        assertThat(exchange.request.getHeaders().get("Authorization").toString())
                .isEqualTo("[Basic YmplbnNlbjpoaWZhbHV0aW4=]");
        assertThat(exchange.request.getUri().getScheme()).isEqualTo("https");
    }

    @Test
    public void testScriptWithParams() throws Exception {
        final Map<String, Object> config = newFileConfigWithArgs("TestGroovyParameters.groovy");
        final ScriptableFilter filter = (ScriptableFilter) new ScriptableFilter.Heaplet().create("test", new JsonValue(
                config), getHeap());

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        final Headers headers = exchange.request.getHeaders();
        assertThat(headers.get("title").toString()).isEqualTo("[Coffee time]");
        assertThat(exchange.response.getStatus()).isEqualTo(418);
        assertThat(exchange.response.getReason()).isEqualTo("Acceptable");
        assertThat(exchange.response.getEntity().toString()).contains("1:koffie, 2:kafe, 3:cafe, 4:kafo");
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

            verifyHttp(server).once(method(Method.GET), Condition.uri("/example"));
            assertThat(exchange.response.getStatus()).isEqualTo(200);
            assertThat(exchange.response.getEntity().getString()).isEqualTo(JSON_CONTENT);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testRequestSetUriWithStringSetter() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.request.uri = 'http://www.example.com/example'",
                "next.handle(exchange)");
        // @formatter:on

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);

        verify(handler).handle(exchange);
        assertThat(exchange.request.getUri().toString()).isEqualTo("http://www.example.com/example");
    }

    @Test
    public void testRequestSetUriWithURISetter() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import java.net.URI",
                "exchange.request.uri = new URI('http://www.example.com/example')",
                "next.handle(exchange)");
        // @formatter:on

        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);

        verify(handler).handle(exchange);
        assertThat(exchange.request.getUri().toString()).isEqualTo("http://www.example.com/example");
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
                        .<LDAPClientContext>newServerConnectionFactory(backend));
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
            exchange.request.getHeaders().add("Username", "bjensen");
            exchange.request.getHeaders().add("Password", "password");
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);
            assertThat(exchange.response.getStatus()).as(exchange.response.getReason()).isEqualTo(200);
            assertThat(exchange.response.getReason()).isEqualTo("test user");

            // Authenticate using wrong password.
            exchange.request = new Request();
            exchange.request.getHeaders().add("Username", "bjensen");
            exchange.request.getHeaders().add("Password", "wrong");
            filter.filter(exchange, handler);
            assertThat(exchange.response.getStatus()).isEqualTo(403);
            assertThat(exchange.response.getReason()).isNotNull();
        } finally {
            listener.close();
        }
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;
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
                "cn: Barbara Jensen",
                "cn: Babs Jensen",
                "sn: Jensen",
                "uid: bjensen",
                "description: test user",
                "userPassword: hifalutin"));
        // @formatter:on
        final LDAPListener listener =
                new LDAPListener(0, Connections
                        .<LDAPClientContext>newServerConnectionFactory(backend));
        final int port = listener.getPort();
        try {
            final Map<String, Object> config = newFileConfig("LdapAuthFilter.groovy");
            final ScriptableFilter filter =
                    (ScriptableFilter) new ScriptableFilter.Heaplet()
                            .create("test", new JsonValue(config), getHeap());

            // Authenticate using correct password.
            final Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.setUri(new URI("http://test?username=bjensen&password=hifalutin"));
            // FixMe: Passing the LDAP host and port as headers is wrong.
            exchange.put("ldapHost", "localhost");
            exchange.put("ldapPort", "" + port);
            exchange.session = new SimpleMapSession();
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);
            Set<String> cnValues = new HashSet<String>();
            cnValues.add("Barbara Jensen");
            cnValues.add("Babs Jensen");
            assertThat(exchange.session.get("cn")).isEqualTo(cnValues);
            assertThat(exchange.session.get("description"))
                    .isEqualTo("New description set by my script");
            assertThat(exchange.request.getHeaders().get("Ldap-User-Dn").toString())
                    .isEqualTo("[uid=bjensen,ou=people,dc=example,dc=com]");

            // Authenticate using wrong password.
            exchange.request = new Request();
            exchange.request.setUri(new URI("http://test?username=bjensen&password=wrong"));
            // FixMe: Passing the LDAP host and port as headers is wrong.
            exchange.request.getHeaders().add("LdapHost", "0.0.0.0");
            exchange.request.getHeaders().add("LdapPort", "" + port);
            filter.filter(exchange, handler);
            assertThat(exchange.response.getStatus()).isEqualTo(403);
            assertThat(exchange.response.getReason()).isNotNull();
        } finally {
            listener.close();
        }
    }

    @Test
    public void testSqlClientFromFile() throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            // Create an in-memory database with a table to hold credentials.
            JdbcDataSource jdbcDataSource = new JdbcDataSource();
            jdbcDataSource.setUrl("jdbc:h2:mem:test");
            jdbcDataSource.setUser("sa");
            jdbcDataSource.setPassword("sa");

            Context context = new InitialContext();
            context.bind("jdbc/forgerock", jdbcDataSource);

            connection = jdbcDataSource.getConnection();

            statement = connection.createStatement();

            final String createTable = "CREATE TABLE USERS("
                    + "USERNAME VARCHAR(255) PRIMARY KEY, "
                    + "PASSWORD VARCHAR(255), "
                    + "EMAIL VARCHAR(255));";
            statement.execute(createTable);

            final String insertCredentials = "INSERT INTO USERS "
                    + "VALUES('bjensen', 'hifalutin', 'bjensen@example.com');";
            statement.execute(insertCredentials);

            // The script can do something like the following.
            final String readTable =
                    "SELECT USERNAME, PASSWORD FROM USERS WHERE EMAIL='bjensen@example.com';";
            resultSet = statement.executeQuery(readTable);
            while (resultSet.next()) {
                assertThat(resultSet.getString("USERNAME")).isEqualTo("bjensen");
                assertThat(resultSet.getString("PASSWORD")).isEqualTo("hifalutin");
            }
            // In-memory database disappears when the last connection is closed.

            final Map<String, Object> config = newFileConfig("SqlAccessFilter.groovy");
            final ScriptableFilter filter =
                    (ScriptableFilter) new ScriptableFilter.Heaplet()
                            .create("test", new JsonValue(config), getHeap());

            final Exchange exchange = new Exchange();
            exchange.request = new Request();
            exchange.request.setUri(new URI("http://test?mail=bjensen@example.com"));
            final Handler handler = mock(Handler.class);
            filter.filter(exchange, handler);
            assertThat(exchange.request.getHeaders().get("Username").toString())
                    .isEqualTo("[bjensen]");
            assertThat(exchange.request.getHeaders().get("Password").toString())
                    .isEqualTo("[hifalutin]");
            assertThat(exchange.request.getUri().getScheme()).isEqualTo("https");
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                if (statement != null) {
                    statement.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception e) {
                // Ignored
            }
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

    @Test(expectedExceptions = HandlerException.class)
    public void testNextHandlerCanThrowHandlerException() throws Exception {
        final ScriptableFilter filter = newGroovyFilter("next.handle(exchange)");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        doThrow(new HandlerException()).when(handler).handle(exchange);

        filter.filter(exchange, handler);
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

    @Test(enabled = true)
    public void testReadJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.entity.json.person.firstName == 'Tim'",
                "assert exchange.request.entity.json.person.lastName == 'Yates'",
                "assert exchange.request.entity.json.person.address.country == 'UK'");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity(JSON_CONTENT);
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
    }

    @Test(enabled = false)
    public void testReadXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "assert exchange.request.entity.xml.root.a"); // TODO
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity(XML_CONTENT);
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
        exchange.request.getHeaders().add("Cookie", "username=test;Path=/");
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
        exchange.request.setUri(new URI("http://test?username=test"));
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
        exchange.request.getHeaders().add("Username", "test");
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(exchange.request.getHeaders().get("Test")).containsOnly("test");
        assertThat(exchange.request.getHeaders().get("Username")).isNull();
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
        exchange.request.setUri(new URI("http://example.com:8080/users?action=create"));
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

        assertThat(exchange.response.getStatus()).isEqualTo(200);
        assertThat(exchange.response.getEntity().getString()).isEqualTo("hello world");
    }

    @Test(expectedExceptions = ScriptException.class)
    public void testRunTimeFailure() throws Throwable {
        final ScriptableFilter filter = newGroovyFilter("dummy + 1");
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        try {
            filter.filter(exchange, handler);
            fail("Script exception expected");
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
        assertThat(exchange.response.getStatus()).isEqualTo(404);
    }

    @Test(expectedExceptions = HandlerException.class, expectedExceptionsMessageRegExp = "test")
    public void testThrowHandlerException() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.openig.handler.HandlerException",
                "throw new HandlerException(\"test\")");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);

        filter.filter(exchange, handler);
    }

    @Test(enabled = false)
    public void testWriteJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import groovy.json.*",
                "exchange.request.entity.json = new JsonBuilder().person {",
                    "firstName 'Tim'",
                    "lastName 'Yates'",
                    "address {",
                        "city 'Manchester'",
                        "country 'UK'",
                        "zip 'M1 2AB'",
                    "}",
                "}");
        // @formatter:on
        final Exchange exchange = new Exchange();
        exchange.request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, handler);
        assertThat(exchange.request.getEntity().getString()).isEqualTo(JSON_CONTENT);
    }

    @Test(enabled = false)
    public void testWriteXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.request.entity.xml.root {",
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
        assertThat(exchange.request.getEntity().getString()).isEqualTo(XML_CONTENT);
    }

    private HeapImpl getHeap() throws Exception {
        final HeapImpl heap = new HeapImpl();
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        heap.put(ENVIRONMENT_HEAP_KEY, getEnvironment());
        heap.put(HTTP_CLIENT_HEAP_KEY, new HttpClient(new TemporaryStorage()));
        return heap;
    }

    private Environment getEnvironment() throws Exception {
        return new DefaultEnvironment(new File(getTestBaseDirectory()));
    }

    /**
     * Implements a strategy to find the directory where groovy scripts are loadable.
     */
    private String getTestBaseDirectory() throws Exception {
        // relative path to our-self
        String name = resource(getClass());
        // find the complete URL pointing to our path
        URL resource = getClass().getClassLoader().getResource(name);

        // Strip out the 'file' scheme
        File f = new File(resource.toURI());
        String path = f.getPath();

        // Strip out the resource path to actually get the base directory
        return path.substring(0, path.length() - name.length());
    }

    private static String resource(final Class<?> type) {
        return type.getName().replace('.', '/').concat(".class");
    }

    private static Map<String, Object> newFileConfig(final String groovyClass) {
        final Map<String, Object> config = new HashMap<String, Object>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);
        return config;
    }

    /**
     * Equivalent to :
     * <p>
     * <pre>
     * {
     *     "name": "groovyFilter",
     *     "type": "ScriptableFilter",
     *     "config": {
     *         "type": "application/x-groovy",
     *         "file": "ScriptWithParams.groovy",
     *         "args": {
     *             "title": "Coffee time",
     *             "status": 418,
     *             "reason": [
     *                 "Not Acceptable",
     *                 "I'm a teapot",
     *                 "Acceptable" ],
     *             "names": {
     *                 "1": "koffie",
     *                 "2": "kafe",
     *                 "3": "cafe",
     *                 "4": "kafo"
     *             }
     *         }
     *     }
     * }
     * </pre>
     */
    private static Map<String, Object> newFileConfigWithArgs(final String groovyClass) {
        final Map<String, Object> config = new HashMap<String, Object>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);
        final Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("title", "Coffee time");
        args.put("status", "418");
        args.put("reason", new String[] { "Not Acceptable", "I'm a teapot", "Acceptable" });
        final Map<String, Object> coffeeNames = new LinkedHashMap<String, Object>();
        coffeeNames.put("1", "koffie");
        coffeeNames.put("2", "kafe");
        coffeeNames.put("3", "cafe");
        coffeeNames.put("4", "kafo");
        args.put("names", coffeeNames);
        config.put("args", args);
        return config;
    }

    private ScriptableFilter newGroovyFilter(final String... sourceLines) throws Exception {
        final Environment environment = getEnvironment();
        final Script script = Script.fromSource(environment, Script.GROOVY_MIME_TYPE, sourceLines);
        return new ScriptableFilter(script);
    }

    private static Stubber returnResponse(final Response response) {
        return doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                final Object[] args = invocation.getArguments();
                ((Exchange) args[0]).response = response;
                return null;
            }
        });
    }

}
