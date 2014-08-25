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
package org.forgerock.http.header;

import static org.forgerock.http.header.LocationHeader.NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.forgerock.http.Request;
import org.forgerock.http.Response;
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
        final LocationHeader lh = LocationHeader.valueOf((String) null);
        assertNull(lh.getLocationUri());
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testLocationHeaderAllowsNullOrEmptyString(final String lHeader) {
        final LocationHeader lh = LocationHeader.valueOf(lHeader);
        assertNull(lh.getLocationUri());
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderFromMessage(final String lHeader) {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));
        response.getHeaders().putSingle(NAME, lHeader);
        assertNotNull(response.getHeaders().get(NAME));

        final LocationHeader lh = LocationHeader.valueOf(response);
        assertEquals(lh.getLocationUri(), lHeader);
    }

    @Test
    public void testLocationHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));

        final LocationHeader lh = LocationHeader.valueOf(response);
        assertNull(lh.getLocationUri());
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderFromString(final String lHeader) {
        final LocationHeader lh = LocationHeader.valueOf(lHeader);
        assertEquals(lh.getLocationUri(), lHeader);
        assertEquals(lh.getName(), NAME);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToMessageRequest(final String lHeader) {
        final Request request = new Request();
        assertNull(request.getHeaders().get(NAME));
        final LocationHeader lh = LocationHeader.valueOf(lHeader);
        request.getHeaders().putSingle(lh);
        assertNotNull(request.getHeaders().get(NAME));
        assertEquals(request.getHeaders().getFirst(NAME), lHeader);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToMessageResponse(final String lHeader) {
        final Response response = new Response();
        assertNull(response.getHeaders().get(NAME));

        final LocationHeader lh = LocationHeader.valueOf(lHeader);
        response.getHeaders().putSingle(lh);

        assertNotNull(response.getHeaders().get(NAME));
        assertEquals(response.getHeaders().get(NAME).get(0), lHeader);
    }

    @Test(dataProvider = "locationHeaderProvider")
    public void testLocationHeaderToString(final String lHeader) {
        final LocationHeader lh = LocationHeader.valueOf(lHeader);
        assertEquals(lh.toString(), lHeader);
    }
}
