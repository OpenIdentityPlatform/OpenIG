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

package org.forgerock.openig.util;

import static java.lang.Long.*;
import static org.assertj.core.api.Assertions.*;
import static org.forgerock.openig.util.Json.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This class is unit testing jackson parser used in OpenIG. Its parser is
 * stricter, gives nicer messages in error cases and easily
 * configurable.
 */
public class JacksonJsonTest {

    private static final String INVALID_JSON = "invalid json";
    private static final String JSON_CONTENT =
            "{ 'name': 'outputHandler', 'type': 'ClientHandler' }";

    @DataProvider
    private Object[][] emptyJson() {
        return new Object[][] {
            { "" },
            { "\n" } };
    }

    @DataProvider
    private Object[][] newLine() {
        return new Object[][] {
            { "\r\n" },
            { "\n" } };
    }

    @DataProvider
    private Object[][] invalidJson() {
        return new Object[][] {
            { "{ 'name':'outputHandler', 'type': 'ClientHandler }," }, // Missing quote within the sample.
            { "{ 'name':, 'type': 'ClientHandler' }," }, // A comma too many.
            { "{ 'name':, 'type': 'ClientHandler' }" }, // Missing attribute value.
            { "{ :'outputHandler', 'type': 'ClientHandler' }" }}; // Missing attribute name.
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldFailSerializingInvalidNullString() throws Exception {
        readJson(new StringReader((String) null));
    }

    @Test(dataProvider = "emptyJson")
    public void shouldReturnsNullWhenSerializingEmptyString(final String json) throws Exception {
        assertThat(readJson(from(json))).isNull();
    }

    @Test(expectedExceptions = IOException.class)
    public void shouldFailToReadInvalidJson() throws Exception {
        readJson(from(INVALID_JSON));
    }

    @Test(dataProvider = "invalidJson", expectedExceptions = IOException.class)
    public void testFailParsingInvalidJson(final String invalid)
            throws Exception {
        readJson(from(invalid));
    }

    @Test
    public void shouldParsingSucceedWhenCommentsEnabledBlockommentInsideBlock() throws Exception {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' /* This is a comment */ } ";
        final Map<String, Object> json = readJsonLenient(from(sample));
        assertThat(json.get("name")).isEqualTo("outputHandler");
        assertThat(json.get("type")).isEqualTo("ClientHandler");
    }

    @Test
    public void shouldParsingSucceedWhenCommentsEnabledBlockCommentOutsideBlock() throws Exception {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' } /* This is a comment */ ";
        final Map<String, Object> json = readJsonLenient(from(sample));
        assertThat(json.get("name")).isEqualTo("outputHandler");
        assertThat(json.get("type")).isEqualTo("ClientHandler");
    }

    @Test
    public void shouldParsingSucceedWhenCommentsEnabledLineCommentOutsideBlock() throws Exception {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' } // This is a comment";
        final Map<String, Object> json = readJsonLenient(from(sample));
        assertThat(json.get("name")).isEqualTo("outputHandler");
        assertThat(json.get("type")).isEqualTo("ClientHandler");
    }

    @Test(expectedExceptions = IOException.class)
    public void shouldParsingFailWhenCommentsEnabledLineCommentInsideBlock() throws Exception {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' // This is a comment }";
        readJsonLenient(from(sample));
    }

    @Test(expectedExceptions = IOException.class)
    public void shouldParsingFailWhenCommentsDisabled() throws Exception {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' /* This is a comment */ } ";
        readJson(from(sample));
    }

    @Test
    public void shouldCommentsIgnoredByParsing() throws IOException {
        final String sample =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' /* This is a comment */ } ";

        final String sample2 =
                "{ 'name': 'outputHandler', 'type': 'ClientHandler' } ";

        assertThat(readJsonLenient(from(sample))).isEqualTo(readJson(from(sample2)));
    }

    @Test(dataProvider = "newLine", expectedExceptions = IOException.class)
    public void shouldThrowIoExceptionWhenContainingEol(final String newLine) throws IOException {
        readJson(singleQuotesToDouble("{ 'comment': 'Two lines " + newLine + "of comment' }"));
    }

    /**
     * The lenient parser should allow use of EOL (included in the string
     * value). It also allows use of ASCII chars < 32.
     * e.g.
     *
     * <pre>
     * "source": "import org.forgerock.json.fluent.JsonValue;
     *            logger.info(exchange.token.asJsonValue() as String);"
     * </pre>
     */
    @Test(dataProvider = "newLine")
    public void shouldSucceedWhenContainingEol(final String newLine) throws IOException {
        final Map<String, Object> jkson =
                readJsonLenient(from("{ 'comment': 'Two lines " + newLine + "of comment' }"));
        assertThat((String) jkson.get("comment")).matches("Two lines [\r\n]{1,2}of comment$");
    }

    @Test
    public void testAllowsSimpleQuote() throws Exception {
        final String sample = "{ 'condition': true } ";
        final Map<String, Object> jkson = readJsonLenient(new StringReader(sample));

        assertThat((Boolean) jkson.get("condition")).isTrue();
    }

    @Test
    public void testFromString() throws Exception {
        final Map<String, Object> jkson = readJson(from(JSON_CONTENT));

        assertThat(jkson.get("name")).isEqualTo("outputHandler");
        assertThat(jkson.get("type")).isEqualTo("ClientHandler");
    }

    @Test
    public void testStringAllowNullAttributeValue() throws Exception {

        final String rawJson = "{ 'name': null, 'type': 'ClientHandler' } ";
        final Map<String, Object> jkson = readJson(from(rawJson));

        assertThat(jkson.get("name")).isNull();
        assertThat(jkson.get("type")).isEqualTo("ClientHandler");
    }

    @Test
    public void testJsonValueNumber() throws Exception {

        final String rawJson = "{ 'number': 23, 'other': '27' } ";
        final Map<String, Object> jkson = readJson(from(rawJson));

        assertThat(jkson.get("number")).isEqualTo(23);
        assertThat(jkson.get("other")).isEqualTo("27");
    }

    @Test
    public void testJsonValueSupportsLong() throws Exception {

        final String rawJson = "{ 'long': '" + MAX_VALUE + "'}";
        final Map<String, Object> jkson = readJson(from(rawJson));

        assertThat(parseLong(jkson.get("long").toString())).isEqualTo(MAX_VALUE);
    }

    @Test
    public void shouldSucceedToParseArray() throws IOException {
        final String rawJson = singleQuotesToDouble("['The', 100 ]");
        final Object jsonNode = readJson(rawJson);
        assertThat(jsonNode).isInstanceOf(List.class);
        final List<?> values = (List<?>) jsonNode;
        assertThat(values.get(0)).isEqualTo("The");
        assertThat(values.get(1)).isEqualTo(100);
    }

    @Test
    public void shouldSucceedToParseArray2() throws IOException {
        final String rawJson = singleQuotesToDouble("['The', { 'data':'outputHandler'} ]");
        final Object jsonNode = readJson(rawJson);
        assertThat(jsonNode).isInstanceOf(List.class);
        final List<?> values = (List<?>) jsonNode;
        assertThat(values.get(0)).isEqualTo("The");
        assertThat(values.get(1)).isInstanceOf(LinkedHashMap.class);
        @SuppressWarnings("unchecked")
        final LinkedHashMap<String, Object> val2 = (LinkedHashMap<String, Object>) values.get(1);
        assertThat(val2.get("data")).isEqualTo("outputHandler");
    }

    @Test
    public void testParsesBoolean() throws IOException {
        final Object jsonNode = readJson("true");
        assertThat(jsonNode).isInstanceOf(Boolean.class).isEqualTo(true);
    }

    /**
     * Returns a string reader from the rawjson attribute.
     *
     * @param rawjson
     *            Json string to read (can be single quoted).
     * @return a string reader.
     */
    private Reader from(final String rawJson) {
        return new StringReader(singleQuotesToDouble(rawJson));
    }

    /** Remove single quotes from a given string. */
    private static String singleQuotesToDouble(final String value) {
        return value.replace('\'', '\"');
    }
}
