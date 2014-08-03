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

package org.forgerock.openig.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.fluent.JsonValue.field;
import static org.forgerock.json.fluent.JsonValue.object;
import static org.forgerock.openig.http.Entity.APPLICATION_JSON_CHARSET_UTF_8;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.forgerock.openig.header.ContentLengthHeader;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.io.BranchingInputStream;
import org.forgerock.openig.io.ByteArrayBranchingStream;
import org.json.simple.parser.ParseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EntityTest {
    private static final String INVALID_JSON = "invalid json";
    private static final String JSON_CONTENT1 = "{\"a\":1,\"b\":2}";
    private static final String JSON_CONTENT2 = "{\"c\":3,\"d\":4}";
    private static final Object JSON_VALUE1 = object(field("a", 1L), field("b", 2L));
    private static final Object JSON_VALUE2 = object(field("c", 3L), field("d", 4L));

    private Entity entity;
    private Request message;
    private BranchingInputStream mockJsonContent1;
    private BranchingInputStream mockJsonContent2;

    @BeforeMethod
    public void beforeMethod() throws Exception {
        message = new Request();
        entity = message.getEntity();
        mockJsonContent1 = mockContent(JSON_CONTENT1);
        mockJsonContent2 = mockContent(JSON_CONTENT2);
    }

    @Test
    public void entityIsEmptyByDefault() throws Exception {
        assertThat(entity.getRawInputStream().available()).isEqualTo(0);
        assertThat(entity.newDecodedContentInputStream().available()).isEqualTo(0);
        assertThat(entity.newDecodedContentReader(null).readLine()).isNull();
        assertThat(entity.getBytes()).isEmpty();
        assertThat(entity.getString()).isEmpty();
        assertThat(entity.toString()).isEmpty();
        entity.push();
        assertThat(entity.getRawInputStream().available()).isEqualTo(0);
        entity.pop();
        entity.close();
    }

    @Test
    public void getBytes() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThat(entity.getBytes()).isEqualTo(bytes(JSON_CONTENT1));
        assertThat(mockJsonContent1.available()).isEqualTo(JSON_CONTENT1.length());
        verify(mockJsonContent1, never()).close();
    }

    @Test
    public void getJson() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThat(entity.getJson()).isEqualTo(JSON_VALUE1);
        verify(mockJsonContent1, never()).close();
    }

    @Test(expectedExceptions = ParseException.class)
    public void getJsonWhenEntityContainsInvalidJsonThrowsParseException() throws Exception {
        mockJsonContent1 = mockContent(INVALID_JSON);
        entity.setRawInputStream(mockJsonContent1);
        try {
            entity.getJson();
        } finally {
            // The stream should be untouched despite the error.
            assertThat(mockJsonContent1.available()).isEqualTo(INVALID_JSON.length());
            verify(mockJsonContent1, never()).close();
        }
    }

    @Test(expectedExceptions = ParseException.class)
    public void getJsonWhenEntityIsEmptyThrowsParseException() throws Exception {
        entity.getJson();
    }

    @Test
    public void getPushAndPop() throws Exception {
        /*
         * We cannot use the mock content here because it is bypassed during
         * calling getParent() on the stream.
         */
        final ByteArrayBranchingStream content = new ByteArrayBranchingStream(bytes(JSON_CONTENT1));
        entity.setRawInputStream(content);
        entity.push();
        assertThat(entity.getRawInputStream()).isNotSameAs(content);
        entity.getRawInputStream().close();
        assertThat(entity.getRawInputStream()).isNotSameAs(content);
        entity.pop();
        assertThat(entity.getRawInputStream()).isSameAs(content);
        assertThat(content.available()).isEqualTo(JSON_CONTENT1.length());
    }

    @Test
    public void getRawInputStream() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThat(entity.getRawInputStream()).isSameAs(mockJsonContent1);
        assertThat(mockJsonContent1.available()).isEqualTo(JSON_CONTENT1.length());
        verify(mockJsonContent1, never()).close();
    }

    @Test
    public void getString() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThat(entity.getString()).isEqualTo(JSON_CONTENT1);
        assertThat(mockJsonContent1.available()).isEqualTo(JSON_CONTENT1.length());
        verify(mockJsonContent1, never()).close();
    }

    @Test
    public void setBytes() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setBytes(bytes(JSON_CONTENT2));
        assertThatContentIsJsonContent2();
        assertThatContentLengthHeaderIsPresentForJsonContent2();
        assertThatContentyTypeHeaderIsNotPresent();
    }

    @Test
    public void setBytesNullBecomesEmptyStream() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setBytes(null);
        assertThat(entity.getRawInputStream().available()).isEqualTo(0);
    }

    @Test
    public void setJson() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setJson(JSON_VALUE2);
        assertThatContentIsJsonContent2();
        assertThatContentLengthHeaderIsPresentForJsonContent2();
        assertThatContentTypeHeaderIsPresent();
    }

    @Test
    public void setJsonNullIsJsonNull() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setJson(null);
        assertThat(entity.getRawInputStream().available()).isEqualTo("null".length());
    }

    @Test
    public void setRawInputStream() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setRawInputStream(mockJsonContent2);
        assertThatContentIsJsonContent2();
        assertThatContentLengthHeaderIsNotPresent();
        assertThatContentyTypeHeaderIsNotPresent();
    }

    @Test
    public void setRawInputStreamNullBecomesEmptyStream() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setRawInputStream(null);
        assertThat(entity.getRawInputStream().available()).isEqualTo(0);
    }

    @Test
    public void setString() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setString(JSON_CONTENT2);
        assertThatContentIsJsonContent2();
        assertThatContentLengthHeaderIsPresentForJsonContent2();
        assertThatContentyTypeHeaderIsNotPresent();
    }

    @Test
    public void setStringNullBecomesEmptyStream() throws Exception {
        entity.setRawInputStream(mockJsonContent1);
        assertThatContentIsJsonContent1();
        entity.setString(null);
        assertThat(entity.getRawInputStream().available()).isEqualTo(0);
    }

    private void assertThatContentIsJsonContent1() throws IOException, ParseException {
        assertThat(entity.getString()).isEqualTo(JSON_CONTENT1);
        assertThat(entity.getJson()).isEqualTo(JSON_VALUE1);
        assertThat(mockJsonContent1.available()).isEqualTo(JSON_CONTENT1.length());
    }

    private void assertThatContentIsJsonContent2() throws IOException,
            UnsupportedEncodingException, ParseException {
        assertThat(entity.getRawInputStream()).isNotSameAs(mockJsonContent1);
        assertThat(entity.getBytes()).isEqualTo(bytes(JSON_CONTENT2));
        assertThat(entity.getString()).isEqualTo(JSON_CONTENT2);
        assertThat(entity.getJson()).isEqualTo(JSON_VALUE2);
        assertThat(entity.getRawInputStream().available()).isEqualTo(JSON_CONTENT2.length());
        verify(mockJsonContent1).close();
    }

    private void assertThatContentLengthHeaderIsNotPresent() {
        assertThat(message.getHeaders().get(ContentLengthHeader.NAME)).isNullOrEmpty();
    }

    private void assertThatContentLengthHeaderIsPresentForJsonContent2() {
        assertThat(message.getHeaders()).containsEntry(ContentLengthHeader.NAME,
                Arrays.asList(String.valueOf(JSON_CONTENT2.length())));
    }

    private void assertThatContentTypeHeaderIsPresent() {
        assertThat(message.getHeaders()).containsEntry(ContentTypeHeader.NAME,
                Arrays.asList(APPLICATION_JSON_CHARSET_UTF_8));
    }

    private void assertThatContentyTypeHeaderIsNotPresent() {
        assertThat(message.getHeaders().get(ContentTypeHeader.NAME)).isNullOrEmpty();
    }

    private byte[] bytes(final String s) throws UnsupportedEncodingException {
        return s.getBytes("UTF-8");
    }

    private BranchingInputStream mockContent(final byte[] bytes) {
        return mock(BranchingInputStream.class, delegatesTo(new ByteArrayBranchingStream(bytes)));
    }

    /**
     * Input streams are painful to mock, so we'll use the new delegating answer
     * support in 1.9.5 to monitor and proxy calls to a real input stream.
     */
    private BranchingInputStream mockContent(final String content)
            throws UnsupportedEncodingException {
        return mockContent(content.getBytes("UTF-8"));
    }

}
