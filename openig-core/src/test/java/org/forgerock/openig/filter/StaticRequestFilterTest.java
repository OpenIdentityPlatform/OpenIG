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
import static org.forgerock.http.MutableUri.uri;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StaticRequestFilterTest {

    public static final String URI = "http://openig.forgerock.org";
    private TerminalHandler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        terminalHandler = new TerminalHandler();
    }

    /**
     * Minimal configuration test.
     * The handler should propagate the uri, method and version.
     */
    @Test
    public void testUriMethodAndVersionPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.setVersion("1.1");

        Exchange exchange = new Exchange();
        filter.filter(exchange, null, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(exchange.request);
        assertThat(terminalHandler.request).isNotNull();
        assertThat(terminalHandler.request.getUri()).isEqualTo(uri(URI));
        assertThat(terminalHandler.request.getMethod()).isEqualTo("GET");
        assertThat(terminalHandler.request.getVersion()).isEqualTo("1.1");
    }

    @Test
    public void testHeadersPropagation() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addHeaderValue("Mono-Valued", Expression.valueOf("First Value", String.class));
        filter.addHeaderValue("Multi-Valued", Expression.valueOf("One (1)", String.class));
        filter.addHeaderValue("Multi-Valued", Expression.valueOf("Two (${exchange.request.version})", String.class));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");
        exchange.request = original;

        filter.filter(exchange, original, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(exchange.request);
        // Check that headers have been properly populated
        assertThat(terminalHandler.request.getHeaders().get("Mono-Valued"))
                .hasSize(1)
                .containsOnly("First Value");
        assertThat(terminalHandler.request.getHeaders().get("Multi-Valued"))
                .hasSize(2)
                .containsOnly("One (1)", "Two (2)");
    }

    @Test
    public void testFormAttributesPropagationWithGetMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("GET");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));
        filter.addFormParameter("multi", Expression.valueOf("one1", String.class));
        filter.addFormParameter("multi", Expression.valueOf("two${exchange.request.version}", String.class));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        exchange.request = new Request();
        exchange.request.setVersion("2");

        filter.filter(exchange, exchange.request, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(exchange.request);
        // Verify that the new request URI contains the form's fields
        assertThat(terminalHandler.request.getUri().toString())
                .startsWith(URI)
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
        assertThat(terminalHandler.request.getEntity().getString()).isEmpty();
    }

    @Test
    public void testFormAttributesPropagationWithPostMethod() throws Exception {
        StaticRequestFilter filter = new StaticRequestFilter("POST");
        filter.setUri(Expression.valueOf(URI, String.class));
        filter.addFormParameter("mono", Expression.valueOf("one", String.class));
        filter.addFormParameter("multi", Expression.valueOf("one1", String.class));
        filter.addFormParameter("multi", Expression.valueOf("two${exchange.request.version}", String.class));

        Exchange exchange = new Exchange();
        // Needed to verify expression evaluation
        Request original = new Request();
        original.setVersion("2");
        exchange.request = original;

        filter.filter(exchange, original, terminalHandler);

        // Verify the request transmitted to downstream filters is not the original one
        assertThat(terminalHandler.request).isNotSameAs(exchange.request);
        // Verify that the new request entity contains the form's fields
        assertThat(terminalHandler.request.getMethod()).isEqualTo("POST");
        assertThat(terminalHandler.request.getHeaders().getFirst("Content-Type")).isEqualTo(
                "application/x-www-form-urlencoded");
        assertThat(terminalHandler.request.getEntity().getString())
                .contains("mono=one")
                .contains("multi=one1")
                .contains("multi=two2");
    }

    private static class TerminalHandler implements Handler {
        Request request;
        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            this.request = request;
            return Promises.newResultPromise(new Response());
        }
    }
}
