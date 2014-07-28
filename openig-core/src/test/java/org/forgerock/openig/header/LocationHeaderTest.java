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
import static org.forgerock.openig.header.LocationHeader.*;
import static org.testng.Assert.*;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for the location header class.
 * <p>
 * See <link>http://www.ietf.org/rfc/rfc2616.txt</link>
 * </p>. Header field example :
 * <pre>
 * Location: http://www.example.org/index.php
 * </pre>
 */
@SuppressWarnings("javadoc")
public class LocationHeaderTest {

    @DataProvider
    private Object[][] locationHeaderProvider() {
        return new Object[][] {
            { "http://www.example.org/index.php" },
            /* This example, is incorrect according to the RFC, however,
             * all popular browsers will accept a relative URL, and it is correct
             * according to the current revision of HTTP/1.1*/
            { "/blog/" }
        };
    }

    @Test
    public void testLocationHeaderAllowsEmptyOrNullString() {
        final LocationHeader lh = new LocationHeader();
        assertNull(lh.getLocationURI());
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testLocationHeaderAllowsNullOrEmptyString(final String lHeader) {
        final LocationHeader lh = new LocationHeader(lHeader);
        assertNull(lh.getLocationURI());
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderFromMessage(final String lHeader) {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, lHeader);
        assertNotNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(response);
        assertEquals(lh.getLocationURI(), lHeader);
    }

    @Test
    public void testLocationHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(response);
        assertNull(lh.getLocationURI());
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderFromString(final String lHeader) {
        final LocationHeader lh = new LocationHeader(lHeader);
        assertEquals(lh.getLocationURI(), lHeader);
        assertEquals(lh.getKey(), NAME);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToMessageRequest(final String lHeader) {
        final Request request = new Request();
        assertNull(request.headers.get(NAME));
        final LocationHeader lh = new LocationHeader(lHeader);
        lh.toMessage(request);
        assertNotNull(request.headers.get(NAME));
        assertEquals(request.headers.getFirst(NAME), lHeader);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToMessageResponse(final String lHeader) {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(lHeader);
        lh.toMessage(response);

        assertNotNull(response.headers.get(NAME));
        assertEquals(response.headers.get(NAME).get(0), lHeader);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testEqualitySucceed(final String lHeader) {
        final LocationHeader lh = new LocationHeader(lHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, lHeader);

        final LocationHeader lh2 = new LocationHeader(response);
        assertEquals(lh2.getLocationURI(), lh.getLocationURI());
        assertEquals(lh2, lh);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testEqualityFails(final String lHeader) {
        final LocationHeader lh = new LocationHeader(lHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, "");

        final LocationHeader lh2 = new LocationHeader(response);
        assertThat(lh2.getLocationURI()).isNotEqualTo(lh.getLocationURI());
        assertThat(lh2).isNotEqualTo(lh);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToString(final String lHeader) {
        final LocationHeader lh = new LocationHeader(lHeader);
        assertEquals(lh.toString(), lHeader);
    }
}
