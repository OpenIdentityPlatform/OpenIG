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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.header;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.header.ConnectionHeader.*;
import static org.testng.Assert.*;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for the connection header class.
 * Header field example :<pre>
 * Connection: close
 * </pre>
 */
@SuppressWarnings("javadoc")
public class ConnectionHeaderTest {

    private static final String ESCAPED_KEEP_ALIVE_VALUE = "\\\"Keep-Alive\\\"";
    private static final String QUOTED_KEEP_ALIVE_VALUE = "'Keep-Alive'";

    @DataProvider
    private Object[][] connectionHeaders() {
        return new Object[][] {
            { "Keep-Alive" },
            { "close" } };
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testConnectionHeaderAllowsNullOrEmptyString(final String cheader) {
        final ConnectionHeader ch = new ConnectionHeader(cheader);
        assertEquals(ch.getTokens().size(), 0);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testConnectionHeaderFromMessageResponse(final String cheader) {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, cheader);
        assertNotNull(response.getHeaders().get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(response);
        assertThat(ch.getKey()).isEqualTo(NAME);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), cheader);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testConnectionHeaderFromMessageRequest(final String cheader) {
        final Request request = new Request();
        assertNull(request.getHeaders().get(NAME));
        request.getHeaders().putSingle(NAME, cheader);
        assertNotNull(request.getHeaders().get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(request);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), cheader);
    }

    @Test
    public void testConnectionHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(response);
        assertEquals(ch.getTokens().size(), 0);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testConnectionHeaderFromString(final String connectionHeader) {
        final ConnectionHeader ch = new ConnectionHeader(connectionHeader);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), connectionHeader);
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testConnectionHeaderFromEscapedString() {
        final ConnectionHeader ch = new ConnectionHeader(ESCAPED_KEEP_ALIVE_VALUE);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), ESCAPED_KEEP_ALIVE_VALUE);
        assertEquals(ch.getKey(), NAME);
    }

    @Test
    public void testConnectionHeaderFromQuotedString() {
        final ConnectionHeader ch = new ConnectionHeader(QUOTED_KEEP_ALIVE_VALUE);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), QUOTED_KEEP_ALIVE_VALUE);
        assertEquals(ch.getKey(), NAME);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testConnectionHeaderToMessageRequest(final String connectionHeader) {
        final Request request = new Request();
        assertNull(request.getHeaders().get(NAME));
        final ConnectionHeader ch = new ConnectionHeader(connectionHeader);
        ch.toMessage(request);
        assertNotNull(request.getHeaders().get(NAME));
        assertEquals(request.getHeaders().getFirst(NAME), connectionHeader);
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testConnectionHeaderToMessageNullOrEmptyDoesNothing(final String cheader) {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(cheader);
        ch.toMessage(response);

        assertNull(response.getHeaders().get(NAME));
    }

    @Test(dataProvider = "connectionHeaders")
    public void testConnectionHeaderToMessageResponse(final String connectionHeader) {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(connectionHeader);
        ch.toMessage(response);

        assertNotNull(response.getHeaders().get(NAME));
        assertEquals(response.getHeaders().getFirst(NAME), connectionHeader);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testEqualitySucceed(final String connectionHeader) {
        final ConnectionHeader lh = new ConnectionHeader(connectionHeader);
        final Response response = new Response();

        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, connectionHeader);

        final ConnectionHeader ch2 = new ConnectionHeader();
        ch2.fromMessage(response);
        assertEquals(ch2.getTokens(), lh.getTokens());
        assertEquals(ch2, lh);
    }

    @Test(dataProvider = "connectionHeaders")
    public void testEqualityFails(final String connectionHeader) {
        final ConnectionHeader ch = new ConnectionHeader(connectionHeader);
        final Response response = new Response();

        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, "Connection");

        final ConnectionHeader ch2 = new ConnectionHeader();
        ch2.fromMessage(response);
        assertThat(ch2.getTokens()).isNotEqualTo(ch.getTokens());
        assertThat(ch2).isNotEqualTo(ch);
    }
}
