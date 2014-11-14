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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.header.ContentLengthHeader.NAME;

import org.forgerock.http.Request;
import org.forgerock.http.Response;
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
        final ContentLengthHeader clh = ContentLengthHeader.valueOf(cheader);
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderSucceedParsingStringValue() {
        final ContentLengthHeader clh = ContentLengthHeader.valueOf("1024");
        assertThat(clh.getLength()).isEqualTo(1024);
        assertThat(clh.getName()).isEqualTo(NAME);
    }

    @Test
    public void testContentLengthHeaderFailsParsingStringValue() {
        final ContentLengthHeader clh = ContentLengthHeader.valueOf("invalidContentLengthHeader");
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderFromMessageResponse() {
        final Response response = new Response();
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, String.valueOf(LENGTH_DEFAULT_VALUE));

        final ContentLengthHeader clh = ContentLengthHeader.valueOf(response);
        assertThat(clh.getName()).isEqualTo(NAME);
        assertThat(clh.getLength()).isEqualTo(LENGTH_DEFAULT_VALUE);
    }

    @Test
    public void testContentLengthHeaderFromMessageResponseFails() {
        final Response response = new Response();
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, "invalid");

        final ContentLengthHeader clh = ContentLengthHeader.valueOf(response);
        assertThat(clh.getName()).isEqualTo(NAME);
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }

    @Test
    public void testContentLengthHeaderToMessageRequest() {
        final Request request = new Request();
        assertThat(request.getHeaders().getFirst(NAME)).isNull();
        final ContentLengthHeader clh =
                ContentLengthHeader.valueOf(String.valueOf(LENGTH_DEFAULT_VALUE));
        // Inserts the content length header to the request header.
        request.getHeaders().putSingle(clh);
        assertThat(request.getHeaders().getFirst(NAME)).isEqualTo(String.valueOf(LENGTH_DEFAULT_VALUE));
    }

    @Test
    public void testContentLengthHeaderToMessageRequestFails() {
        final Request request = new Request();
        assertThat(request.getHeaders().getFirst(NAME)).isNull();
        final ContentLengthHeader clh = ContentLengthHeader.valueOf("invalid_value");
        // Inserts the content length header to the request header.
        request.getHeaders().putSingle(clh);
        assertThat(request.getHeaders().getFirst(NAME)).isNullOrEmpty();
    }

    @Test(dataProvider = "validDataProvider")
    public void testContentLengthHeaderToStringSucceed(final Object cth) {
        final ContentLengthHeader clh = ContentLengthHeader.valueOf(String.valueOf(cth));
        assertThat(clh.toString()).isEqualTo(String.valueOf(cth));
    }

    @Test( dataProvider = "invalidDataProvider")
    public void testContentLengthHeaderToStringIsNullWithInvalidValues(final Object cth) {
        final ContentLengthHeader clh = ContentLengthHeader.valueOf(String.valueOf(cth));
        assertThat(clh.getLength()).isEqualTo(-1);
        assertThat(clh.toString()).isNull();
    }
}
