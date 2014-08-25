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
import static org.forgerock.http.header.ContentTypeHeader.NAME;

import java.nio.charset.Charset;

import org.forgerock.http.Message;
import org.forgerock.http.Request;
import org.forgerock.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class tests the content type header. see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.17.
 * boundary see http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.2 .
 * Header field example :
 * <pre>
 * Content-Type: text/html; charset=utf-8
 * </pre>
 */
@SuppressWarnings("javadoc")
public class ContentTypeHeaderTest {

    /** An invalid content type header - The separator is not correct. */
    private static final String INVALID_CT_HEADER = "text/html# charset=ISO-8859-4";

    @DataProvider
    private Object[][] contentTypeHeaderProvider() {
        return new Object[][] {
            // content-type | type | charset | boundary
            { "image/gif", "image/gif", null, null },
            { "text/html; charset=ISO-8859-4", "text/html", "ISO-8859-4", null },
            { "multipart/mixed; boundary=gc0p4Jq0M2Yt08jU534c0p", "multipart/mixed", null,
                "gc0p4Jq0M2Yt08jU534c0p" },
            { "text/html; charset=utf-8", "text/html", "UTF-8", null } };
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testContentTypeHeaderAllowsNullOrEmptyString(final String cheader) {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        assertThat(cth.getType()).isNull();
        assertThat(cth.getCharset()).isNull();
        assertThat(cth.getBoundary()).isNull();
        assertThat(cth.toString()).isNull();
    }

    @Test
    public void testContentTypeHeaderFromNullMessage() {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf((Message) null);
        assertThat(cth.getType()).isNull();
        assertThat(cth.getCharset()).isNull();
        assertThat(cth.getBoundary()).isNull();
        assertThat(cth.toString()).isNull();
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromString(final String cheader, final String type, final String charset,
            final String boundary) {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderToMessageRequest(final String cheader, final String type, final String charset,
            final String boundary) {
        final Request request = new Request();
        assertThat(request.getHeaders().get(NAME)).isNull();
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        request.getHeaders().putSingle(cth);
        assertThat(request.getHeaders().get(NAME)).isNotEmpty();
        assertThat(request.getHeaders().getFirst(NAME)).isEqualTo(cheader);
    }

    @Test
    public void testContentTypeHeaderFromInvalidString() {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(INVALID_CT_HEADER);
        assertThat(cth.getType()).isEqualTo(INVALID_CT_HEADER);
        assertThat(cth.getCharset()).isEqualTo(null);
        assertThat(cth.getBoundary()).isEqualTo(null);
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromMessageResponse(final String cheader, final String type, final String charset,
            final String boundary) {
        // Creates response.
        final Response response = new Response();
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().putSingle(NAME, cheader);
        assertThat(response.getHeaders().get(NAME)).isNotNull();

        // Creates content-type header from response.
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(response);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromMessageRequest(final String cheader, final String type, final String charset,
            final String boundary) {
        // Creates request.
        final Request request = new Request();
        assertThat(request.getHeaders().get(NAME)).isNull();
        request.getHeaders().putSingle(NAME, cheader);
        assertThat(request.getHeaders().get(NAME)).isNotNull();

        // Creates content-type header from request.
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(request);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
    }
}
