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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.get;
import static com.xebialabs.restito.semantics.Condition.method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.openig.heap.Keys.ENVIRONMENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.HTTP_CLIENT_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.File;
import java.io.IOException;
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

import javax.naming.InitialContext;
import javax.script.ScriptException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.openig.config.Environment;
import org.forgerock.openig.config.env.DefaultEnvironment;
import org.forgerock.openig.filter.ScriptableFilter.Heaplet;
import org.forgerock.openig.handler.ScriptableHandler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.log.NullLogSink;
import org.forgerock.openig.script.Script;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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

    private ResponseHandler successHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        this.successHandler = new ResponseHandler(new Response(Status.OK));
    }

    @Test
    public void testAssignment() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "exchange.attributes.test = false",
                "next.handle(context, request)",
                "    .thenOnResult({ response -> ",
                "      exchange.attributes.test = (response.status.code == 302)",
                "})"
        );
        // @formatter:on
        final Response response = new Response();
        response.setStatus(Status.FOUND);
        final Handler handler = new ResponseHandler(response);
        Exchange exchange = new Exchange();
        filter.filter(exchange, null, handler);
        assertThat((Boolean) exchange.getAttributes().get("test")).isTrue();
    }

    @Test
    public void testBindingsArePresent() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert exchange != null",
                "assert contexts != null",
                "assert logger != null",
                "assert ldap != null",
                "assert globals != null",
                "assert request != null",
                "assert context != null",
                "assert next != null",
                "new Response(Status.OK)");
        // @formatter:on
        final Handler handler = mock(Handler.class);
        Response response = filter.filter(new Exchange(), new Request(), handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        verifyZeroInteractions(handler);
    }

    @DataProvider
    public static Object[][] invalidScripts() {
        // @Checkstyle:off
        return new Object[][] {
                { "import does.not.Exist" },
                { "import static does.not.Exist" },
                { "try {" },
                { "http://www.example.com" },
                { "as Promise" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidScripts", expectedExceptions = ScriptException.class)
    public void testCompilationFailure(String script) throws Exception {
        newGroovyFilter(script);
    }

    @Test
    public void testConstructFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("TestFileBasedScript.groovy");
        final ScriptableFilter filter =
                (ScriptableFilter) new Heaplet().create(Name.of("test"), new JsonValue(
                        config), getHeap());

        final Handler handler = mock(Handler.class);
        Response response = filter.filter(new Exchange(), null, handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        verifyZeroInteractions(handler);
    }

    @Test
    public void testDispatchFromFile() throws Exception {
        final Map<String, Object> config = newFileConfig("DispatchHandler.groovy");
        final ScriptableHandler handler =
                (ScriptableHandler) new ScriptableHandler.Heaplet().create(
                        Name.of("test"), new JsonValue(config), getHeap());

        // Try with valid credentials
        Request one = new Request();
        one.setUri(new URI("http://test/login"));
        one.getHeaders().add("Username", "bjensen");
        one.getHeaders().add("Password", "hifalutin");
        Promise<Response, NeverThrowsException> promise1 = handler.handle(new Exchange(), one);
        assertThat(promise1.get().getStatus()).isEqualTo(Status.OK);

        // Try with invalid credentials
        Request two = new Request();
        two.setUri(new URI("http://test/login"));
        two.getHeaders().add("Username", "bob");
        two.getHeaders().add("Password", "dobbs");
        Promise<Response, NeverThrowsException> promise2 = handler.handle(new Exchange(), two);
        assertThat(promise2.get().getStatus()).isEqualTo(Status.FORBIDDEN);

        // Try with different path
        Request three = new Request();
        three.setUri(new URI("http://test/index.html"));
        Promise<Response, NeverThrowsException> promise3 = handler.handle(new Exchange(), three);
        assertThat(promise3.get().getStatus()).isEqualTo(Status.UNAUTHORIZED);
    }

    @Test
    public void testBasicAuthFilterFromFile() throws Exception {
        final Map<String, Object> config = newFileConfigWithCredentials("BasicAuthFilter.groovy");
        final ScriptableFilter filter =
                (ScriptableFilter) new Heaplet().create(Name.of("test"), new JsonValue(
                        config), getHeap());

        Request request = new Request();
        request.setUri(new URI("http://www.example.com/"));
        Exchange exchange = new Exchange();
        Response response = filter.filter(exchange, request, successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        // base64-encode "bjensen:hifalutin" -> "YmplbnNlbjpoaWZhbHV0aW4="
        assertThat(request.getHeaders().get("Authorization").getValues())
                .containsOnly("Basic YmplbnNlbjpoaWZhbHV0aW4=");
        assertThat(request.getUri().getScheme()).isEqualTo("https");
    }

    @Test
    public void testScriptWithParams() throws Exception {
        final Map<String, Object> config = newFileConfigWithArgs("TestGroovyParameters.groovy");
        final ScriptableFilter filter = (ScriptableFilter) new Heaplet().create(Name.of("test"),
                                                                                new JsonValue(config),
                                                                                getHeap());

        Request request = new Request();
        Response response = filter.filter(new Exchange(), request, successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.TEAPOT);
        final Headers headers = request.getHeaders();
        assertThat(headers.get("title").getValues()).containsOnly("Coffee time");
        assertThat(response.getEntity().toString()).contains("1:koffie, 2:kafe, 3:cafe, 4:kafo");
    }

    @Test
    public void testConstructFromString() throws Exception {
        final String script =
                "import org.forgerock.http.protocol.*;new Response(Status.OK)";
        final Map<String, Object> config = new HashMap<>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("source", script);
        final ScriptableFilter filter =
                (ScriptableFilter) new Heaplet().create(Name.of("test"), new JsonValue(
                        config), getHeap());

        final Handler handler = mock(Handler.class);
        Response response = filter.filter(new Exchange(), new Request(), handler).get();
        verifyZeroInteractions(handler);
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void testGlobalsPersistedBetweenInvocations() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert globals.x == null",
                "globals.x = 'value'",
                "new Response(Status.OK)");
        // @formatter:on
        Response one = filter.filter(new Exchange(), null, null).get();
        assertThat(one.getStatus()).isEqualTo(Status.OK);
        Response two = filter.filter(new Exchange(), null, null).get();
        assertThat(two.getStatus()).isNotEqualTo(Status.OK);
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
                    "import org.forgerock.http.protocol.*",
                    "import org.forgerock.services.context.RootContext",
                    "Request request = new Request()",
                    "request.method = 'GET'",
                    "request.uri = new URI('http://0.0.0.0:" + port + "/example')",
                    "http.executeAsync(new RootContext(), request)");
            filter.setHttpClient(new HttpClient());

            // @formatter:on
            Response response = filter.filter(new Exchange(), new Request(), null).get();

            verifyHttp(server).once(method(Method.GET), Condition.uri("/example"));
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            assertThat(response.getEntity().getString()).isEqualTo(JSON_CONTENT);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testRequestSetUriWithStringSetter() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "request.uri = 'http://www.example.com/example'",
                "next.handle(context, request)");
        // @formatter:on

        Request request = new Request();
        Response response = filter.filter(new Exchange(), request, successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);

        assertThat(request.getUri().toString())
                .isEqualTo("http://www.example.com/example");
    }

    @Test
    public void testRequestSetUriWithURISetter() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import java.net.URI",
                "request.uri = new URI('http://www.example.com/example')",
                "next.handle(context, request)");
        // @formatter:on

        final Exchange exchange = new Exchange();
        Request request = new Request();
        TerminalHandler handler = new TerminalHandler();
        Response response = filter.filter(exchange, request, handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);

        assertThat(handler.request).isSameAs(request);
        assertThat(handler.request.getUri().toString()).isEqualTo("http://www.example.com/example");
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
                    "import org.forgerock.http.protocol.Response",
                    "import org.forgerock.http.protocol.Status",
                    "",
                    "username = request.headers.Username.values[0]",
                    "password = request.headers.Password.values[0]",
                    "",
                    "Response response = new Response()",
                    "",
                    "client = ldap.connect('0.0.0.0'," + port + ")",
                    "try {",
                    "  user = client.searchSingleEntry('ou=people,dc=example,dc=com',",
                    "                                  ldap.scope.sub,",
                    "                                  ldap.filter('(uid=%s)', username))",
                    "  client.bind(user.name.toString(), password.toCharArray())",
                    "  response.status = Status.OK",
                    // Attributes as MetaClass properties
                    "  user.description = 'some value'",
                    "  assert user.description.parse().asString() == 'some value'",
                    "  user.description = ['one', 'two']",
                    "  assert user.description.parse().asSetOfString() == ['one', 'two'] as Set",
                    "  user.description += 'three'",
                    "  assert user.description.parse().asSetOfString() == ['one', 'two', 'three'] as Set",
                    "} catch (AuthenticationException e) {",
                    "  response.status = Status.FORBIDDEN",
                    "} catch (Exception e) {",
                    "  response.status = Status.INTERNAL_SERVER_ERROR",
                    "} finally {",
                    "  client.close()",
                    "}",
                    "Response.newResponsePromise(response)");
            // @formatter:on

            // Authenticate using correct password.
            Request authorizedRequest = new Request();
            authorizedRequest.getHeaders().add("Username", "bjensen");
            authorizedRequest.getHeaders().add("Password", "password");
            Response response = filter.filter(new Exchange(), authorizedRequest, null).get();
            assertThat(response.getStatus()).isEqualTo(Status.OK);

            // Authenticate using wrong password.
            Request request = new Request();
            request.getHeaders().add("Username", "bjensen");
            request.getHeaders().add("Password", "wrong");
            Response response1 = filter.filter(new Exchange(), request, null).get();
            assertThat(response1.getStatus()).isEqualTo(Status.FORBIDDEN);
        } finally {
            listener.close();
        }
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
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
                    (ScriptableFilter) new Heaplet()
                            .create(Name.of("test"), new JsonValue(config), getHeap());

            // Authenticate using correct password.
            final Exchange exchange = new Exchange();
            Request request = new Request();
            request.setUri(new URI("http://test?username=bjensen&password=hifalutin"));
            // FixMe: Passing the LDAP host and port as headers is wrong.
            exchange.getAttributes().put("ldapHost", "localhost");
            exchange.getAttributes().put("ldapPort", String.valueOf(port));
            filter.filter(new SessionContext(exchange, new SimpleMapSession()), request, mock(Handler.class));

            Set<String> cnValues = new HashSet<>();
            cnValues.add("Barbara Jensen");
            cnValues.add("Babs Jensen");
            assertThat(exchange.getAttributes().get("cn")).isEqualTo(cnValues);
            assertThat(exchange.getAttributes().get("description"))
                    .isEqualTo("New description set by my script");
            assertThat(request.getHeaders().get("Ldap-User-Dn").getValues())
                    .containsOnly("uid=bjensen,ou=people,dc=example,dc=com");

            // Authenticate using wrong password.
            Request request2 = new Request();
            request2.setUri(new URI("http://test?username=bjensen&password=wrong"));
            // FixMe: Passing the LDAP host and port as headers is wrong.
            request2.getHeaders().add("LdapHost", "0.0.0.0");
            request2.getHeaders().add("LdapPort", "" + port);
            Response response = filter.filter(exchange, request2, mock(Handler.class)).get();
            assertThat(response.getStatus()).isEqualTo(Status.FORBIDDEN);
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

            InitialContext context = new InitialContext();
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
                    (ScriptableFilter) new Heaplet()
                            .create(Name.of("test"), new JsonValue(config), getHeap());

            final Exchange exchange = new Exchange();
            Request request = new Request();
            request.setUri(new URI("http://test?mail=bjensen@example.com"));
            Response response = filter.filter(exchange, request, successHandler).get();
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            assertThat(request.getHeaders().get("Username").getValues())
                    .containsOnly("bjensen");
            assertThat(request.getHeaders().get("Password").getValues())
                    .containsOnly("hifalutin");
            assertThat(request.getUri().getScheme()).isEqualTo("https");
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
        final ScriptableFilter filter = newGroovyFilter("logger.error('test')",
                                                        "next.handle(context, request)");
        final Exchange exchange = new Exchange();
        filter.setLogger(mock(Logger.class));
        Response response = filter.filter(exchange, new Request(), successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        verify(filter.getLogger()).error("test");
    }

    @Test
    public void testNextHandlerCanBeInvoked() throws Exception {
        final ScriptableFilter filter = newGroovyFilter("next.handle(context, request)");
        final Exchange exchange = new Exchange();
        Request request = new Request();
        final Handler handler = mock(Handler.class);
        filter.filter(exchange, request, handler);
        verify(handler).handle(exchange, request);
    }

    @Test
    public void shouldFailBecauseScriptDoNotReturnPromiseOrResponse() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "next.handle(context, request)",
                "42");
        // @formatter:on
        final Response expectedResponse = new Response();
        final Handler handler = new ResponseHandler(expectedResponse);
        Response response = filter.filter(new Exchange(), new Request(), handler).get();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
    }

    @DataProvider
    public static Object[][] returnTypes() {
        // @Checkstyle:off
        return new Object[][] {
                { "return next.handle(context, request)" },
                { "next.handle(context, request)" },
                { "import org.forgerock.http.protocol.*; return new Response(Status.OK)" },
                { "import org.forgerock.http.protocol.*; new Response(Status.OK)" },
                { "import org.forgerock.http.protocol.*; return Response.newResponsePromise(new Response(Status.OK))" },
                { "import org.forgerock.http.protocol.*; Response.newResponsePromise(new Response(Status.OK))" },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "returnTypes")
    public void shouldConvertIntoPromiseOfResponse(final String content) throws Exception {
        ScriptableFilter filter = newGroovyFilter(content);
        Response response = filter.filter(new Exchange(), new Request(), successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void testReadJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.entity.json.person.firstName == 'Tim'",
                "assert request.entity.json.person.lastName == 'Yates'",
                "assert request.entity.json.person.address.country == 'UK'",
                "new Response(Status.OK)");
        // @formatter:on
        Request request = new Request();
        request.setEntity(JSON_CONTENT);
        filter.filter(new Exchange(), request, null).getOrThrow();
    }

    @Test(enabled = false)
    public void testReadXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.entity.xml.root.a",
                "new Response(Status.OK)"); // TODO
        // @formatter:on
        Request request = new Request();
        request.setEntity(XML_CONTENT);
        Response response = filter.filter(new Exchange(), request, null).getOrThrow();
        assertThat(response.getStatus()).isNotEqualTo(Status.OK);
    }

    @Test
    public void testRequestCookies() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.cookies.username[0].value == 'test'",
                "new Response(Status.OK)");
        // @formatter:on
        Request request = new Request();
        request.getHeaders().add("Cookie", "username=test;Path=/");
        Response response = filter.filter(new Exchange(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void testRequestForm() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.form.username[0] == 'test'",
                "new Response(Status.OK)");
        // @formatter:on
        Request request = new Request();
        request.setUri(new URI("http://test?username=test"));
        Response response = filter.filter(new Exchange(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void testRequestHeaders() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.headers.Username.values[0] == 'test'",
                "request.headers.Test = [ 'test' ]",
                "assert request.headers.remove('Username')",
                "new Response(Status.OK)");
        // @formatter:on
        Request request = new Request();
        request.getHeaders().add("Username", "test");
        Response response = filter.filter(new Exchange(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.OK);

        assertThat(request.getHeaders().get("Test").getValues()).containsOnly("test");
        assertThat(request.getHeaders().get("Username")).isNull();
    }

    @Test
    public void testRequestURI() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert request.uri.scheme == 'http'",
                "assert request.uri.host == 'example.com'",
                "assert request.uri.port == 8080",
                "assert request.uri.path == '/users'",
                "assert request.uri.query == 'action=create'",
                "new Response(Status.OK)");
        // @formatter:on
        Request request = new Request();
        request.setUri(new URI("http://example.com:8080/users?action=create"));
        Response response = filter.filter(new Exchange(), request, null).getOrThrow();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
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
                "import org.forgerock.http.protocol.*",
                "response = new Response(Status.OK)",
                "response.entity = 'hello world'",
                "return response");
        // @formatter:on
        Request request = new Request();
        Response response = filter.filter(new Exchange(), request, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).isEqualTo("hello world");
    }

    @Test(dataProvider = "failingScripts")
    public void testRunTimeFailure(String script) throws Exception {
        final ScriptableFilter filter = newGroovyFilter(script);
        Response response = filter.filter(new Exchange(), null, null).get();
        assertThat(response.getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
        assertThat(response.getEntity().getString()).contains("Cannot execute script");
    }

    @DataProvider
    public static Object[][] failingScripts() {
        // @Checkstyle:off
        return new Object[][] {
                { "dummy + 1" },
                { "throw new Exception('test')" }
        };
        // @Checkstyle:on
    }

    @Test
    public void testSession() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "assert contexts.session.session.inKey == 'inValue'",
                "contexts.session.session.outKey = 'outValue'",
                "assert contexts.session.session.remove('inKey')",
                "return new Response(Status.OK)");
        // @formatter:on
        Session session = new SimpleMapSession();
        session.put("inKey", "inValue");
        Response response = filter.filter(new SessionContext(null, session), null, null).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(session).doesNotContainKey("inKey");
        assertThat(session).containsExactly(entry("outKey", "outValue"));
    }

    @Test
    public void testSetResponse() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import org.forgerock.http.protocol.*",
                "new Response(Status.NOT_FOUND)");
        // @formatter:on
        final Exchange exchange = new Exchange();
        Response response = filter.filter(exchange, null, null).getOrThrow();
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
    }

    @Test(enabled = false)
    public void testWriteJsonEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "import groovy.json.*",
                "request.entity.json = new JsonBuilder().person {",
                    "firstName 'Tim'",
                    "lastName 'Yates'",
                    "address {",
                        "city 'Manchester'",
                        "country 'UK'",
                        "zip 'M1 2AB'",
                    "}",
                "}");
        // @formatter:on
        Request request = new Request();
        filter.filter(new Exchange(), request, null);
        assertThat(request.getEntity().getString()).isEqualTo(JSON_CONTENT);
    }

    @Test(enabled = false)
    public void testWriteXmlEntity() throws Exception {
        // @formatter:off
        final ScriptableFilter filter = newGroovyFilter(
                "request.entity.xml.root {",
                "a( a1:'one' ) {",
                "b { mkp.yield( '3 < 5' ) }",
                "c( a2:'two', 'blah' )",
                "}",
                "}");
        // @formatter:on
        Request request = new Request();
        filter.filter(new Exchange(), request, null);
        assertThat(request.getEntity().getString()).isEqualTo(XML_CONTENT);
    }

    @Test
    public void shouldExecuteGroovyClosuresAsPromiseFunctions() throws Exception {
        ScriptableFilter filter = (ScriptableFilter) new Heaplet().create(Name.of("test"),
                                                                          json(newFileConfig("Closures.groovy")),
                                                                          getHeap());

        Response response = filter.filter(new Exchange(), null, successHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.ACCEPTED);
        assertThat(response.getHeaders().get("X-Test").getValues()).containsOnly("Been there, done that");
    }

    private HeapImpl getHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("anonymous"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new NullLogSink());
        heap.put(ENVIRONMENT_HEAP_KEY, getEnvironment());
        heap.put(HTTP_CLIENT_HEAP_KEY, new HttpClient());
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
        final Map<String, Object> config = new HashMap<>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);
        return config;
    }

    /**
     * Equivalent to :
     * <p>
     * <pre>
     * {@code
     * {
     *     "name": "BasicAuth",
     *     "type": "ScriptableFilter",
     *     "config": {
     *         "type": "application/x-groovy",
     *         "file": "BasicAuthFilter.groovy",
     *         "args": {
     *             "username": "bjensen",
     *             "password": "hifalutin"
     *             }
     *         }
     * }
     * }
     * </pre>
     */
    private static Map<String, Object> newFileConfigWithCredentials(final String groovyClass) {
        final Map<String, Object> config = new HashMap<>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);

        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("username", "bjensen");
        args.put("password", "hifalutin");
        config.put("args", args);
        return config;
    }

    /**
     * Equivalent to :
     * <p>
     * <pre>
     * {@code
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
     * }
     * </pre>
     */
    private static Map<String, Object> newFileConfigWithArgs(final String groovyClass) {
        final Map<String, Object> config = new HashMap<>();
        config.put("type", Script.GROOVY_MIME_TYPE);
        config.put("file", groovyClass);
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", "Coffee time");
        args.put("status", "418");
        args.put("reason", new String[] { "Not Acceptable", "I'm a teapot", "Acceptable" });
        final Map<String, Object> coffeeNames = new LinkedHashMap<>();
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

    private static class TerminalHandler implements Handler {
        Request request;
        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            this.request = request;
            return Promises.newResultPromise(new Response(Status.OK));
        }
    }

}
