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

import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.header.CookieHeader;
import org.forgerock.openig.http.Cookie;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.http.Session;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;

import static org.fest.assertions.Assertions.*;
import static org.mockito.Mockito.*;

public class CookieFilterTest {

    private Exchange exchange;

    @Mock
    private Handler terminalHandler;

    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.uri = new URI("http://openig.example.org");

        session = new SimpleMapSession();
        exchange.session = session;
    }


    /**
     * Managed Cookies are cookies received from the next handler in chain.
     * They should not come from the original client.
     * If so, the filter should remove the original cookie and replace it by the managed one.
     */
    @Test
    public void testManagedCookiesAreOverridingTheOriginalCookieValueFromTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.managed.add("Test-Managed");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // As the terminal handler is a mock, prepare an empty Response in the exchange
                exchange.response = new Response();

                // Expecting to find the managed cookie, not the original one
                // CookieFilter produces a single 'Cookie' value
                assertThat(exchange.request.headers.getFirst("Cookie"))
                        .contains("Test-Managed=\"Overridden value\"");

                // request.cookies is not in sync with the message's headers' content
                // Cannot assert on request.cookies due to OPENIG-123
                // assertFalse(exchange.request.cookies.containsKey("Test-Managed"));
                return null;
            }
        }).when(terminalHandler).handle(exchange);

        // Prepare the manager with a managed cookie to transmit in place of the original one
        CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        manager.getCookieStore().add(exchange.request.uri, buildCookie("Test-Managed", "Overridden value"));
        session.put(CookieManager.class.getName(), manager);

        // Prepare the request with an existing cookie that will be overridden
        appendRequestCookie("Test-Managed", ".example.org");

        assertThat(exchange.request.cookies.containsKey("Test-Managed")).isTrue();
        filter.filter(exchange, terminalHandler);

    }

    /**
     * Assume that a request comes with a managed cookie inside.
     * It should be hidden to the next handler in chain.
     */
    @Test
    public void testClientCookiesThatAreManagedAreNotTransmittedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.managed.add("Test-Managed");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // As the terminal handler is a mock, prepare an empty Response in the exchange
                exchange.response = new Response();

                // As the cookie should have been removed, we should not have any Cookie header now
                // Refers to OPENIG-124 (empty 'Cookie' header or no header at all)
                assertThat(exchange.request.headers.get("Cookie")).isEmpty();

                return null;
            }
        }).when(terminalHandler).handle(exchange);

        // Prepare the request with an existing cookie that will be overridden
        appendRequestCookie("Test-Managed", ".example.org");

        filter.filter(exchange, terminalHandler);
    }

    /**
     * Assume that a request comes with a managed cookie inside.
     * It should be hidden to the next handler in chain.
     */
    @Test
    public void testSuppressedClientCookiesAreNotTransmittedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.suppressed.add("Will-Be-Deleted");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // As the terminal handler is a mock, prepare an empty Response in the exchange
                exchange.response = new Response();

                // As the cookie should have been removed, we should not have any Cookie header now
                // Refers to OPENIG-124 (empty 'Cookie' header or no header at all)
                assertThat(exchange.request.headers.get("Cookie")).isEmpty();

                return null;
            }
        }).when(terminalHandler).handle(exchange);

        // Prepare the request with an existing cookie
        appendRequestCookie("Will-Be-Deleted", ".example.org");

        filter.filter(exchange, terminalHandler);
    }

    /**
     * Assume that a request comes with a cookie inside.
     * It should be relayed to the next handler in chain.
     */
    @Test
    public void testRelayedClientCookiesAreTransmittedUnchangedToTheUserAgent() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.relayed.add("Will-Be-Relayed");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // As the terminal handler is a mock, prepare an empty Response in the exchange
                exchange.response = new Response();

                assertThat(exchange.request.headers.getFirst("Cookie"))
                        .contains("Will-Be-Relayed=\"Default Value\"");

                return null;
            }
        }).when(terminalHandler).handle(exchange);

        // Prepare the request with an existing cookie
        appendRequestCookie("Will-Be-Relayed", ".example.org");

        filter.filter(exchange, terminalHandler);
    }

    private HttpCookie buildCookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        return cookie;
    }

    @Test
    public void testManagedCookiesInResponseAreNotPropagatedBackToTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.managed.add("Hidden-Cookie");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // Populate the response with a cookie that should be invisible to client
                exchange.response = new Response();
                exchange.response.headers.putSingle("Set-cookie2", "Hidden-Cookie=value");

                return null;
            }
        }).when(terminalHandler).handle(exchange);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.response.headers.get("Set-cookie2")).isEmpty();
    }

    @Test
    public void testSuppressedCookiesInResponseAreNotPropagatedBackToTheClient() throws Exception {

        CookieFilter filter = new CookieFilter();
        filter.suppressed.add("Suppressed-Cookie");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // Populate the response with a cookie that should be invisible to client
                exchange.response = new Response();
                exchange.response.headers.putSingle("Set-cookie2", "Suppressed-Cookie=value");

                return null;
            }
        }).when(terminalHandler).handle(exchange);

        filter.filter(exchange, terminalHandler);

        assertThat(exchange.response.headers.get("Set-cookie2")).isEmpty();
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
        filter.managed.add("Managed");

        // Step #2: by default, no cookies are provisioned in the request

        // Step #4
        // Mock the first 'next handler' invocation (returns the cookie)
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // Populate the response with a cookie that should be invisible to client
                exchange.response = new Response();
                exchange.response.headers.putSingle("Set-cookie2", "Managed=value");

                return null;
            }
        }).when(terminalHandler).handle(exchange);


        // First call
        filter.filter(exchange, terminalHandler);

        // Second call in the same session

        // Prepare the mock objects
        final Exchange exchange2 = new Exchange();
        exchange2.session = session;
        exchange2.request = new Request();
        exchange2.request.uri = new URI("http://openig.example.org");
        exchange2.response = null;

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {

                // Ensure the next handler have the cookie
                String cookie = exchange2.request.headers.getFirst("Cookie");
                assertThat(cookie).isEqualTo("Managed=value");

                // Prepare a stubbed Response to avoid NPE
                exchange2.response = new Response();
                return null;
            }
        }).when(terminalHandler).handle(exchange2);

        // Perform the call
        filter.filter(exchange2, terminalHandler);

        assertThat(exchange2.response.headers.get("Set-cookie2")).isNullOrEmpty();

    }

    private void appendRequestCookie(String name, String domain) {
        Cookie cookie = new Cookie();
        cookie.name = name;
        cookie.value = "Default Value";
        cookie.domain = domain;
        cookie.path = "/";
        CookieHeader header = new CookieHeader(exchange.request);
        header.cookies.add(cookie);

        // Serialize the newly created Cookie inside the request
        header.toMessage(exchange.request);
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session { }
}