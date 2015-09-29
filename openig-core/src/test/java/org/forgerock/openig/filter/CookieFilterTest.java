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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;

import org.forgerock.http.Handler;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.http.header.CookieHeader;
import org.forgerock.http.protocol.Cookie;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CookieFilterTest {

    @Mock
    private Handler terminalHandler;

    private SessionContext context;
    private Session session;
    private Request request;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        request = new Request();
        request.setUri("http://openig.example.org");

        session = new SimpleMapSession();
        context = new SessionContext(null, session);
    }


    /**
     * Managed Cookies are cookies received from the next handler in chain.
     * They should not come from the original client.
     * If so, the filter should remove the original cookie and replace it by the managed one.
     */
    @Test
    public void testManagedCookiesAreOverridingTheOriginalCookieValueFromTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getManaged().add("Test-Managed");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {
                        // Expecting to find the managed cookie, not the original one
                        // CookieFilter produces a single 'Cookie' value
                        assertThat(request.getHeaders().getFirst(CookieHeader.NAME))
                                .contains("Test-Managed=\"Overridden value\"");

                        // request.cookies is not in sync with the message's headers' content
                        // Cannot assert on request.cookies due to OPENIG-123
                        // assertFalse(exchange.request.cookies.containsKey("Test-Managed"));

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Prepare the manager with a managed cookie to transmit in place of the original one
        CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        manager.getCookieStore()
               .add(request.getUri().asURI(),
                    buildCookie("Test-Managed", "Overridden value"));
        session.put(CookieManager.class.getName(), manager);

        // Prepare the request with an existing cookie that will be overridden
        appendRequestCookie("Test-Managed", ".example.org");

        assertThat(request.getCookies().containsKey("Test-Managed")).isTrue();
        filter.filter(context, request, terminalHandler).get();

    }

    /**
     * Assume that a request comes with a managed cookie inside.
     * It should be hidden to the next handler in chain.
     */
    @Test
    public void testClientCookiesThatAreManagedAreNotTransmittedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getManaged().add("Test-Managed");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // As the cookie should have been removed, we should not have any Cookie header now
                        assertThat(request.getHeaders().get(CookieHeader.NAME)).isNull();

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Prepare the request with an existing cookie that will be overridden
        appendRequestCookie("Test-Managed", ".example.org");

        filter.filter(context, request, terminalHandler).get();
    }

    /**
     * Assume that a request comes with a suppressed cookie inside.
     * It should be hidden to the next handler in chain.
     */
    @Test
    public void testSuppressedClientCookiesAreNotTransmittedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getSuppressed().add("Will-Be-Deleted");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // As the cookie should have been removed, we should not have any Cookie header now
                        assertThat(request.getHeaders().get(CookieHeader.NAME)).isNull();

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Prepare the request with an existing cookie
        appendRequestCookie("Will-Be-Deleted", ".example.org");

        filter.filter(context, request, terminalHandler).get();
    }

    /**
     * Assume that a request comes with a suppressed cookie inside.
     * It should be hidden to the next handler in chain.
     * But the relayed cookie should be kept
     */
    @Test
    public void testOnlySuppressedClientCookiesAreNotTransmittedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getSuppressed().add("Will-Be-Deleted");
        filter.getRelayed().add("Will-Not-Be-Deleted");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // As the cookie should have been removed, we should have only 1 cookie
                        List<Cookie> cookies = request.getHeaders().get(CookieHeader.class).getCookies();
                        assertThat(cookies).hasSize(1);
                        assertThat(cookies.get(0).getName()).isEqualTo("Will-Not-Be-Deleted");

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Prepare the request with existing cookies
        appendRequestCookie("Will-Be-Deleted", ".example.org");
        appendRequestCookie("Will-Not-Be-Deleted", ".example.org");

        filter.filter(context, request, terminalHandler).get();
    }

    /**
     * Assume that a request comes with a cookie inside.
     * It should be relayed to the next handler in chain.
     */
    @Test
    public void testRelayedClientCookiesAreTransmittedUnchangedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getRelayed().add("Will-Be-Relayed");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        assertThat(request.getHeaders().getFirst(CookieHeader.NAME))
                                .contains("Will-Be-Relayed=\"Default Value\"");

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Prepare the request with an existing cookie
        appendRequestCookie("Will-Be-Relayed", ".example.org");

        filter.filter(context, request, terminalHandler).get();
    }

    private HttpCookie buildCookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        return cookie;
    }

    @Test
    public void testManagedCookiesInResponseAreNotPropagatedBackToTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getManaged().add("Hidden-Cookie");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // Populate the response with a cookie that should be invisible to client
                        Response response = new Response();
                        response.getHeaders().put("Set-cookie2", "Hidden-Cookie=value");

                        return Promises.newResultPromise(response);
                    }
                });

        Response response = filter.filter(context, request, terminalHandler).get();
        assertThat(response.getHeaders().get("Set-cookie2")).isNull();
    }

    @Test
    public void testSuppressedCookiesInResponseAreNotPropagatedBackToTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.getSuppressed().add("Suppressed-Cookie");

        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // Populate the response with a cookie that should be invisible to client
                        Response response = new Response();
                        response.getHeaders().put("Set-cookie2", "Suppressed-Cookie=value");

                        return Promises.newResultPromise(response);
                    }
                });

        Response response = filter.filter(context, request, terminalHandler).get();
        assertThat(response.getHeaders().get("Set-cookie2")).isNull();
    }

    /**
     * Scenario:
     * ----------------------------------------------------------------
     * <ol>
     *   <li>The filter is configured to manage Cookie named 'Managed'</li>
     *   <li>A request comes from a client, there is no Cookie inside</li>
     *   <li>As there is no cookie in the original request, message is passed as-is to the next
     *            handler (to the user agent)</li>
     *   <li>The response come back with a 'Set-cookie2' header containing the 'Managed' cookie</li>
     *   <li>The filter does the following:
     *     <ol>
     *       <li>Stores it inside the CookieManager for future usage</li>
     *       <li>Removes it from the Response sent back to the client</li>
     *     </ol>
     *   </li>
     *   <li>A new request comes in the same Http Session (and through the same filter), still without any cookies</li>
     *   <li>The filter add the 'Managed' cookie in the request' message and execute the next handler</li>
     *   <li>Continue execution as usual ...</li>
     * </ol>
     *
     * The point to verify here is that the cookie from the user-agent is kept in the session
     * and re-used in sub-sequent requests.
    */
    @Test
    public void testManagedCookiesArePersistentlySendToTheUserAgentOverTheSession() throws Exception {

        // Step #1
        CookieFilter filter = new CookieFilter();
        filter.getManaged().add("Managed");

        // Step #2: by default, no cookies are provisioned in the request

        // Step #4
        // Mock the first 'next handler' invocation (returns the cookie)
        when(terminalHandler.handle(context, request))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // Populate the response with a cookie that should be invisible to client
                        Response response = new Response();
                        response.getHeaders().put("Set-cookie2", "Managed=value");

                        return Promises.newResultPromise(response);
                    }
                });

        // First call
        filter.filter(context, request, terminalHandler).get();

        // Second call in the same session

        // Prepare the mock objects
        final SessionContext context2 = new SessionContext(null, session);
        final Request request2 = new Request();
        request2.setUri("http://openig.example.org");

        when(terminalHandler.handle(context2, request2))
                .then(new Answer<Promise<Response, NeverThrowsException>>() {
                    @Override
                    public Promise<Response, NeverThrowsException> answer(final InvocationOnMock invocation)
                            throws Throwable {

                        // Ensure the next handler have the cookie
                        String cookie = request2.getHeaders().getFirst(CookieHeader.NAME);
                        assertThat(cookie).isEqualTo("Managed=value");

                        return Promises.newResultPromise(new Response());
                    }
                });

        // Perform the call
        Response response = filter.filter(context2, request2, terminalHandler).get();
        assertThat(response.getHeaders().get("Set-cookie2")).isNull();
    }

    private void appendRequestCookie(String name, String domain) {
        Cookie cookie = new Cookie();
        cookie.setName(name);
        cookie.setValue("Default Value");
        cookie.setDomain(domain);
        cookie.setPath("/");
        CookieHeader header = CookieHeader.valueOf(request);
        header.getCookies().add(cookie);

        // Serialize the newly created Cookie inside the request
        request.getHeaders().put(header);
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }
}
