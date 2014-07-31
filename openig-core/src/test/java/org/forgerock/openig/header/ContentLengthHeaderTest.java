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
import static org.forgerock.openig.header.ContentLengthHeader.*;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class is unit testing the Content length header class.
 * The Content length header represents the length of the request body in octets (8-bit bytes).
 * Header field example :<pre>
 * Content-Length: 348
 * </pre>
 */
@SuppressWarnings("javadoc")
public class ContentLengthHeaderTest {

    private static final int LENGTH_DEFAULT_VALUE = 1024;

    @DataProvider
    private Object[][] validDataProvider() {
        return new Object[][] {
            { "10" },
            { 10 },
            { LENGTH_DEFAULT_VALUE } };
    }

    @DataProvider
    private Object[][] invalidDataProvider() {
        return new Object[][] {
            { "-1" },
            { -99 },
            { "invalid_length_header" } };
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testContentLengthHeaderAllowsNullOrEmptyString(final String cheader) {
        final ContentLengthHeader clh = new ContentLengthHeader(cheader);
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderSucceedParsingStringValue() {
        final ContentLengthHeader clh = new ContentLengthHeader("1024");
        assertThat(clh.getLength()).isEqualTo(1024);
        assertThat(clh.getKey()).isEqualTo(NAME);
    }

    @Test
    public void testContentLengthHeaderFailsParsingStringValue() {
        final ContentLengthHeader clh = new ContentLengthHeader("invalidContentLengthHeader");
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderFromMessageResponse() {
        final Response response = new Response();
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, String.valueOf(LENGTH_DEFAULT_VALUE));

        final ContentLengthHeader clh = new ContentLengthHeader(response);
        assertThat(clh.getKey()).isEqualTo(NAME);
        assertThat(clh.getLength()).isEqualTo(LENGTH_DEFAULT_VALUE);
    }

    @Test
    public void testContentLengthHeaderFromMessageResponseFails() {
        final Response response = new Response();
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, "invalid");

        final ContentLengthHeader clh = new ContentLengthHeader(response);
        assertThat(clh.getKey()).isEqualTo(NAME);
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderToMessageRequest() {
        final Request request = new Request();
        assertThat(request.getHeaders().getFirst(NAME)).isNull();
        final ContentLengthHeader clh = new ContentLengthHeader(String.valueOf(LENGTH_DEFAULT_VALUE));
        // Inserts the content length header to the request header.
        clh.toMessage(request);
        assertThat(request.getHeaders().getFirst(NAME)).isEqualTo(String.valueOf(LENGTH_DEFAULT_VALUE));
    }

    @Test
    public void testContentLengthHeaderToMessageRequestFails() {
        final Request request = new Request();
        assertThat(request.getHeaders().getFirst(NAME)).isNull();
        final ContentLengthHeader clh = new ContentLengthHeader("invalid_value");
        // Inserts the content length header to the request header.
        clh.toMessage(request);
        assertThat(request.getHeaders().getFirst(NAME)).isNullOrEmpty();
    }

    @Test(dataProvider = "validDataProvider")
    public void testContentLengthHeaderToStringSucceed(final Object cth) {
        final ContentLengthHeader clh = new ContentLengthHeader(String.valueOf(cth));
        assertThat(clh.toString()).isEqualTo(String.valueOf(cth));
    }

    // VROM Test disabled as an invalid value should not be registered in the content length
    // header. Eg. for -99, the length should be -1. Potential vulnerability.
    @Test(enabled = false, dataProvider = "invalidDataProvider")
    public void testContentLengthHeaderToStringIsNullWithInvalidValues(final Object cth) {
        final ContentLengthHeader clh = new ContentLengthHeader(String.valueOf(cth));
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testEqualitySucceed() {
        final ContentLengthHeader clh = new ContentLengthHeader(String.valueOf(LENGTH_DEFAULT_VALUE));
        final Response response = new Response();

        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, String.valueOf(LENGTH_DEFAULT_VALUE));

        final ContentLengthHeader clh2 = new ContentLengthHeader();
        clh2.fromMessage(response);

        assertThat(clh).isInstanceOf(ContentLengthHeader.class);
        assertThat(clh2).isEqualTo(clh);
    }

    @Test
    public void testEqualityFails() {
        final ContentLengthHeader lh = new ContentLengthHeader(String.valueOf(LENGTH_DEFAULT_VALUE));
        final Response response = new Response();

        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, new ConnectionHeader("Keep-Alive").toString());

        final ConnectionHeader lh2 = new ConnectionHeader(response);

        assertThat(lh).isNotEqualTo(lh2);
    }
}
