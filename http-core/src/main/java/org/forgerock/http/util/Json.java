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
 * Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.http.util;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static java.lang.String.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Provides read and write JSON capabilities.
 * Can check if an object reference is JSON-compatible (expressed as primitive values, list/array and map).
 */
public final class Json {

    /** Non strict object mapper / data binder used to read json configuration files/data. */
    private static final ObjectMapper LENIENT_MAPPER;
    static {
        LENIENT_MAPPER = new ObjectMapper();
        LENIENT_MAPPER.configure(ALLOW_COMMENTS, true);
        LENIENT_MAPPER.configure(ALLOW_SINGLE_QUOTES, true);
        LENIENT_MAPPER.configure(ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }

    /** Strict object mapper / data binder used to read json configuration files/data. */
    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper();

    /**
     * Private constructor for utility class.
     */
    private Json() { }

    /**
     * Verify that the given parameter object is of a JSON compatible type (recursively). If no exception is thrown that
     * means the parameter can be used in the JWT session (that is a JSON value).
     *
     * @param trail
     *         pointer to the verified object
     * @param value
     *         object to verify
     */
    public static void checkJsonCompatibility(final String trail, final Object value) {

        // Null is OK
        if (value == null) {
            return;
        }

        Class<?> type = value.getClass();
        Object object = value;

        // JSON supports Boolean
        if (object instanceof Boolean) {
            return;
        }

        // JSON supports Chars (as String)
        if (object instanceof Character) {
            return;
        }

        // JSON supports Numbers (Long, Float, ...)
        if (object instanceof Number) {
            return;
        }

        // JSON supports String
        if (object instanceof CharSequence) {
            return;
        }

        // Consider array like a List
        if (type.isArray()) {
            object = Arrays.asList((Object[]) value);
        }

        if (object instanceof List) {
            List<?> list = (List<?>) object;
            for (int i = 0; i < list.size(); i++) {
                checkJsonCompatibility(format("%s[%d]", trail, i), list.get(i));
            }
            return;
        }

        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                checkJsonCompatibility(format("%s/%s", trail, entry.getKey()), entry.getValue());
            }
            return;
        }

        throw new IllegalArgumentException(format(
                "The object referenced through '%s' cannot be safely serialized as JSON",
                trail));
    }

    /**
     * Parses to json the provided data.
     *
     * @param rawData
     *            The data as a string to read and parse.
     * @param <T>
     *            The parsing should be as specified in doc. e.g:
     * @see Json#readJson(Reader)
     * @return According to its type, a cast must be necessary to extract the
     *         value.
     * @throws IOException
     *             If an exception occurs during parsing the data.
     */
    public static <T> T readJson(final String rawData) throws IOException {
        if (rawData == null) {
            return null;
        }
        return readJson(new StringReader(rawData));
    }

    /**
     * Parses to json the provided reader.
     *
     * @param reader
     *            The data to parse.
     * @param <T>
     *            The parsing should be as specified in doc. e.g:
     *
     *            <pre>
     * <b>JSON       | Type Java Type</b>
     * ------------------------------------
     * object     | LinkedHashMap<String,?>
     * array      | LinkedList<?>
     * string     | String
     * number     | Integer
     * float      | Float
     * true|false | Boolean
     * null       | null
     * </pre>
     * @return The parsed JSON into its corresponding java type.
     * @throws IOException
     *             If an exception occurs during parsing the data.
     */
    public static <T> T readJson(final Reader reader) throws IOException {
        return parse(STRICT_MAPPER, reader);
    }

    /**
     * This function it's only used to read our configuration files and allows
     * JSON files to contain non strict JSON such as comments or single quotes.
     *
     * @param reader
     *            The stream of data to parse.
     * @return A map containing the parsed configuration.
     * @throws IOException
     *             If an error occurs during reading/parsing the data.
     */
    public static Map<String, Object> readJsonLenient(final Reader reader) throws IOException {
        return parse(LENIENT_MAPPER, reader);
    }

    /**
     * This function it's only used to read our configuration files and allows
     * JSON files to contain non strict JSON such as comments or single quotes.
     *
     * @param in
     *            The input stream containing the json configuration.
     * @return A map containing the parsed configuration.
     * @throws IOException
     *             If an error occurs during reading/parsing the data.
     */
    public static Map<String, Object> readJsonLenient(final InputStream in) throws IOException {
        return parse(LENIENT_MAPPER, new InputStreamReader(in));
    }

    private static <T> T parse(ObjectMapper mapper, Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        final JsonParser jp = mapper.getFactory().createParser(reader);
        final JsonToken jToken = jp.nextToken();
        if (jToken != null) {
            switch (jToken) {
                case START_ARRAY:
                    return mapper.readValue(jp, new TypeReference<LinkedList<?>>() {
                    });
                case START_OBJECT:
                    return mapper.readValue(jp, new TypeReference<LinkedHashMap<String, ?>>() {
                    });
                case VALUE_FALSE:
                case VALUE_TRUE:
                    return mapper.readValue(jp, new TypeReference<Boolean>() {
                    });
                case VALUE_NUMBER_INT:
                    return mapper.readValue(jp, new TypeReference<Integer>() {
                    });
                case VALUE_NUMBER_FLOAT:
                    return mapper.readValue(jp, new TypeReference<Float>() {
                    });
                case VALUE_NULL:
                    return null;
                default:
                    // This is very unlikely to happen.
                    throw new IOException("Invalid JSON content");
            }
        }
        return null;
    }

    /**
     * Writes the JSON content of the object passed in parameter.
     *
     * @param objectToWrite
     *            The object we want to serialize as JSON output. The
     * @return the Json output as a string.
     * @throws IOException
     *             If an error occurs during writing/mapping content.
     */
    public static byte[] writeJson(final Object objectToWrite) throws IOException {
        return STRICT_MAPPER.writeValueAsBytes(objectToWrite);
    }
}
