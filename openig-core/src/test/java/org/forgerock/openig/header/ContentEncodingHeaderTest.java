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

import static com.forgerock.opendj.util.StaticUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.header.ContentEncodingHeader.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;

import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This class is unit testing the content encoding class.
 * See <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a>
 * §14.11
 * Header field example :
 * <pre>
 * Content-Encoding: gzip
 * </pre>
 */
public class ContentEncodingHeaderTest {

    private static final String STRING_TO_DECODE = "People have forgotten this truth, the fox said."
            + " But you mustn't forget it. You become responsible forever for what you've tamed.";

    /**
     * See <a href="http://en.wikipedia.org/wiki/HTTP_compression">List of content encoding</a>
     */
    @DataProvider
    private Object[][] contentEncodingHeaders() {
        return new Object[][] {
            { "gzip" },
            { "deflate" },
            { "identity" },
            { "pack200-gzip" },
            { "compress" } };
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testContentEncodingHeaderAllowsNullOrEmptyString(final String cheader) {
        final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
        assertThat(ceh.toString()).isNull();
    }

    @Test(dataProvider = "contentEncodingHeaders")
    public void testContentEncodingHeaderFromMessageResponse(final String cheader) {
        final Response response = new Response();
        assertThat(response.headers.get(NAME)).isNull();
        response.headers.putSingle(NAME, cheader);

        final ContentEncodingHeader ceh = new ContentEncodingHeader(response);
        assertThat(ceh.getKey()).isEqualTo(NAME);
        assertThat(ceh.getCodings().size()).isEqualTo(1);
        assertThat(ceh.getCodings().get(0)).isEqualTo(cheader);
    }

    @Test(dataProvider = "contentEncodingHeaders")
    public void testContentEncodingHeaderFromMessageRequest(final String cheader) {
        final Request request = new Request();
        assertThat(request.headers.get(NAME)).isNull();
        request.headers.putSingle(NAME, cheader);

        final ContentEncodingHeader ceh = new ContentEncodingHeader(request);
        assertThat(ceh.getCodings().size()).isEqualTo(1);
        assertThat(ceh.getCodings().get(0)).isEqualTo(cheader);
    }

    @Test
    public void testContentEncodingHeaderFromEmptyMessage() {
        final Response response = new Response();
        assertThat(response.headers.get(NAME)).isNull();

        final ContentEncodingHeader ch = new ContentEncodingHeader(response);
        assertThat(ch.getCodings()).isEmpty();
        assertThat(ch.getCodings().size()).isEqualTo(0);
    }

    @Test(dataProvider = "contentEncodingHeaders")
    public void testContentEncodingHeaderToMessageRequest(final String cheader) {
        final Request request = new Request();
        assertThat(request.headers.getFirst(NAME)).isNull();
        final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
        ceh.toMessage(request);

        assertThat(request.headers.getFirst(NAME)).isEqualTo(cheader);
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testContentEncodingHeaderToMessageNullOrEmptyDoesNothing(final String cheader) {
        final Response response = new Response();
        assertThat(response.headers.get(NAME)).isNull();

        final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
        ceh.toMessage(response);

        assertThat(response.headers.get(NAME)).isNull();
    }

    @Test(dataProvider = "contentEncodingHeaders")
    public void testEqualitySucceed(final String cheader) {
        final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
        final Response response = new Response();

        assertThat(response.headers.get(NAME)).isNull();
        response.headers.putSingle(NAME, cheader);

        final ContentEncodingHeader ceh2 = new ContentEncodingHeader();
        ceh2.fromMessage(response);

        assertThat(ceh2).isInstanceOf(ContentEncodingHeader.class);
        assertThat(ceh2.getCodings()).isEqualTo(ceh.getCodings());
        assertThat(ceh2).isEqualTo(ceh);
    }

    @Test(dataProvider = "contentEncodingHeaders")
    public void testEqualityFails(final String cheader) {
        final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
        final Response response = new Response();

        assertThat(response.headers.get(NAME)).isNull();
        response.headers.putSingle(NAME, new ConnectionHeader("Keep-Alive").toString());

        final ConnectionHeader ch2 = new ConnectionHeader(response);

        assertThat(ceh).isNotEqualTo(ch2);
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testDecodeWithNullOrEmptyDecoderHasNoEffectOnInputStream(final String cheader) throws Exception {
        InputStream in = null;
        InputStream out = null;
        try {
            final ContentEncodingHeader ceh = new ContentEncodingHeader(cheader);
            in = new ByteArrayInputStream(getStringToDecodeToCompressedBytes());
            out = ceh.decode(in);
            // The decode has no effects.
            assertThat(in).isEqualTo(out);
        } finally {
            closeSilently(in, out);
        }
    }

    @Test
    public void testDecodeSucceedEncodingGzipHeader() throws Exception {
        BufferedReader br = null;
        try {
            byte[] compressedBytes = getStringToDecodeToCompressedBytes();

            final ContentEncodingHeader ceh = new ContentEncodingHeader("gzip");
            br = new BufferedReader(new InputStreamReader(ceh.decode(new ByteArrayInputStream(compressedBytes))));
            String line;
            while ((line = br.readLine()) != null) {
                assertThat(line).isEqualTo(STRING_TO_DECODE);
            }
        } finally {
            closeSilently(br);
        }
    }

    private byte[] getStringToDecodeToCompressedBytes() throws Exception {
        final ByteArrayOutputStream baostream = new ByteArrayOutputStream();
        final OutputStream outStream = new GZIPOutputStream(baostream);
        try {
            outStream.write(STRING_TO_DECODE.getBytes("UTF-8"));
            outStream.close();
            return baostream.toByteArray();
        } finally {
            closeSilently(baostream, outStream);
        }
    }

    @Test(expectedExceptions = UnsupportedEncodingException.class)
    public void testDecodeDoesNotSupportCompressContentEncoding() throws Exception {

        final String toDecode = "The 'compress' content encoding header is not supported.";
        final InputStream is = new ByteArrayInputStream(toDecode.getBytes());
        new ContentEncodingHeader("compress").decode(is);
    }
}
