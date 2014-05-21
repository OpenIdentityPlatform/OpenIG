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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.openig.header;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.*;
import static org.forgerock.openig.header.ConnectionHeader.NAME;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for the connection header class.
 */
public class ConnectionHeaderTest {

    @DataProvider(name = "connectionHeaders")
    public Object[][] connectionHeadersProvider() {
        return new Object[][] {
            new Object[] { "Keep-Alive" },
            new Object[] { "close" } };
    }

    @DataProvider(name = "nullOrEmptyConnectionHeaderString")
    public Object[][] nullOrEmptyDataProvider() {
        return new Object[][] {
            new Object[] { "" },
            new Object[] { null } };
    }

    @Test(dataProvider = "nullOrEmptyConnectionHeaderString")
    public void connectionHeaderAllowsNullOrEmptyString(final String cheader) {
        final ConnectionHeader ch = new ConnectionHeader(cheader);
        assertEquals(ch.getTokens().size(), 0);
    }

    @Test(dataProvider = "connectionHeaders")
    public void connectionHeaderFromMessageResponse(final String cheader) {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, cheader);
        assertNotNull(response.headers.get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(response);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), cheader);
    }

    @Test(dataProvider = "connectionHeaders")
    public void connectionHeaderFromMessageRequest(final String cheader) {
        final Request request = new Request();
        assertNull(request.headers.get(NAME));
        request.headers.putSingle(NAME, cheader);
        assertNotNull(request.headers.get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(request);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), cheader);
    }

    @Test
    public void connectionHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(response);
        assertEquals(ch.getTokens().size(), 0);
    }

    @Test(dataProvider = "connectionHeaders")
    public void connectionHeaderFromString(final String connectionHeader) {
        final ConnectionHeader ch = new ConnectionHeader(connectionHeader);
        assertEquals(ch.getTokens().size(), 1);
        assertEquals(ch.getTokens().get(0), connectionHeader);
        assertEquals(ch.getKey(), NAME);
    }

    @Test(dataProvider = "connectionHeaders")
    public void connectionHeaderToMessageRequest(final String connectionHeader) {
        final Request request = new Request();
        assertNull(request.headers.get(NAME));
        final ConnectionHeader lh = new ConnectionHeader(connectionHeader);
        lh.toMessage(request);
        assertNotNull(request.headers.get(NAME));
        assertEquals(request.headers.getFirst(NAME), connectionHeader);
    }

    @Test(dataProvider = "nullOrEmptyConnectionHeaderString")
    public void connectionHeaderToMessageNullOrEmptyDoesNothing(final String cheader) {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final ConnectionHeader ch = new ConnectionHeader(cheader);
        ch.toMessage(response);

        assertNull(response.headers.get(NAME));
    }

    @Test(dataProvider = "connectionHeaders")
    public void connectionHeaderToMessageResponse(final String connectionHeader) {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final ConnectionHeader lh = new ConnectionHeader(connectionHeader);
        lh.toMessage(response);

        assertNotNull(response.headers.get(NAME));
        assertEquals(response.headers.getFirst(NAME), connectionHeader);
    }

    @Test(dataProvider = "connectionHeaders")
    public void equalitySucceed(final String connectionHeader) {
        final ConnectionHeader lh = new ConnectionHeader(connectionHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, connectionHeader);

        final ConnectionHeader lh2 = new ConnectionHeader();
        lh2.fromMessage(response);
        assertEquals(lh2.getTokens(), lh.getTokens());
        assertEquals(lh2, lh);
    }

    @Test(dataProvider = "connectionHeaders")
    public void equalityFails(final String connectionHeader) {
        final ConnectionHeader lh = new ConnectionHeader(connectionHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, "Connection");

        final ConnectionHeader lh2 = new ConnectionHeader();
        lh2.fromMessage(response);
        assertThat(lh2.getTokens()).isNotEqualTo(lh.getTokens());
        assertThat(lh2).isNotEqualTo(lh);
    }
}
