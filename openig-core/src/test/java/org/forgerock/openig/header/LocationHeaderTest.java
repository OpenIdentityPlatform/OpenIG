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
import static org.forgerock.openig.header.LocationHeader.NAME;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.Test;

/**
 * Unit tests for the location header class.
 * <p>
 * See <link>http://www.ietf.org/rfc/rfc2616.txt</link>
 * </p>
 */
public class LocationHeaderTest {

    private static String locationHeader = "http://www.example.org/index.php";
    private static String locationHeader2 = "http://www.sample.org/index.php";

    @Test()
    public void locationHeaderAllowsEmptyString() {
        final LocationHeader lh = new LocationHeader();
        assertNull(lh.getLocationURI());
    }

    @Test()
    public void locationHeaderDoesAllowNullString() {
        final LocationHeader lh = new LocationHeader((String) null);
        assertNull(lh.getLocationURI());
    }

    @Test()
    public void locationHeaderFromMessage() {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, locationHeader);
        assertNotNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(response);
        assertEquals(lh.getLocationURI(), locationHeader);
    }

    @Test()
    public void locationHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(response);
        assertNull(lh.getLocationURI());
    }

    @Test()
    public void locationHeaderFromString() {
        final LocationHeader lh = new LocationHeader(locationHeader);
        assertEquals(lh.getLocationURI(), "http://www.example.org/index.php");
        assertEquals(lh.getKey(), NAME);
    }

    @Test()
    public void locationHeaderToMessageRequest() {
        final Request request = new Request();
        assertNull(request.headers.get(NAME));
        final LocationHeader lh = new LocationHeader(locationHeader);
        lh.toMessage(request);
        assertNotNull(request.headers.get(NAME));
        assertEquals(request.headers.get(NAME).get(0), locationHeader);
    }

    @Test()
    public void locationHeaderToMessageResponse() {
        final Response response = new Response();
        assertNull(response.headers.get(NAME));

        final LocationHeader lh = new LocationHeader(locationHeader);
        lh.toMessage(response);

        assertNotNull(response.headers.get(NAME));
        assertEquals(response.headers.get(NAME).get(0), locationHeader);
    }

    @Test()
    public void equalitySucceed() {
        final LocationHeader lh = new LocationHeader(locationHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, "http://www.example.org/index.php");

        final LocationHeader lh2 = new LocationHeader(response);
        assertEquals(lh2.getLocationURI(), lh.getLocationURI());
        assertEquals(lh2, lh);
    }

    @Test()
    public void equalityFails() {
        final LocationHeader lh = new LocationHeader(locationHeader);
        final Response response = new Response();

        assertNull(response.headers.get(NAME));
        response.headers.putSingle(NAME, locationHeader2);

        final LocationHeader lh2 = new LocationHeader(response);
        assertThat(lh2.getLocationURI()).isNotEqualTo(lh.getLocationURI());
        assertThat(lh2).isNotEqualTo(lh);
    }

    @Test()
    public void locationHeaderToString() {
        final LocationHeader lh = new LocationHeader(locationHeader);
        assertEquals(lh.getLocationURI(), locationHeader);
    }
}
