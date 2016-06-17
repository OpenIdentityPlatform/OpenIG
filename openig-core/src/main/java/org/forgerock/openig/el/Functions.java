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
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.openig.el;

import static org.forgerock.openig.util.StringUtil.asString;
import static org.forgerock.util.Utils.closeSilently;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.forgerock.http.util.Uris;
import org.forgerock.openig.util.StringUtil;
import org.forgerock.util.encode.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods exposed for EL usage.
 */
public final class Functions {

    private static final Logger logger = LoggerFactory.getLogger(Functions.class);

    private Functions() { }

    /**
     * Create an array of String based on the strings given as parameters.
     * @param values the strings to put in the array.
     * @return the array of strings.
     */
    public static String[] array(String... values) {
        return values;
    }

    /**
     * Transforms a {@link String} to an {@link Integer}. If the parameter is not a valid integer (in radix 10) then
     * it returns {@literal null}.
     * @param value the {@link String} containing the integer representation to be parsed
     * @return the integer represented by the string argument in the radix 10.
     */
    public static Integer integer(String value) {
        return integerWithRadix(value, 10);
    }

    /**
     * Transforms a {@link String} to an {@link Integer}. If the parameter is not a valid integer then it returns
     * {@literal null}.
     * @param value the {@link String} containing the integer representation to be parsed
     * @param radix the radix to be used while parsing {@code s}.
     * @return the integer represented by the string argument in the specified radix.
     */
    public static Integer integerWithRadix(String value, int radix) {
        try {
            return Integer.parseInt(value, radix);
        } catch (NumberFormatException e) {
            logger.warn("Not recognized as a number : {}", value, e);
            return null;
        }
    }

    /**
     * Transforms a {@link String} to an {@link Boolean}. The rules for the transformation are the same as the ones
     * described on {@link Boolean#valueOf(String)}.
     * @param value the {@link String} containing the boolean representation to be parsed
     * @return the boolean represented by the string argument.
     */
    public static Boolean bool(String value) {
        return Boolean.valueOf(value);
    }

    /**
     * Returns {@code true} if the object contains the value.
     *
     * @param object the object to be searched.
     * @param value the value to find.
     * @return the length of the object, or {@code 0} if length could not be
     * determined.
     */
    public static boolean contains(Object object, Object value) {
        if (object == null || value == null) {
            return false;
        } else if (object instanceof CharSequence && value instanceof CharSequence) {
            return object.toString().contains(value.toString());
        } else if (object instanceof Collection) {
            return ((Collection<?>) object).contains(value);
        } else if (object instanceof Object[]) {
            // doesn't handle primitives (but is cheap)
            for (Object o : (Object[]) object) {
                if (o.equals(value)) {
                    return true;
                }
            }
        } else if (object.getClass().isArray()) {
            // handles primitives (slightly more expensive)
            int length = Array.getLength(object);
            for (int n = 0; n < length; n++) {
                if (Array.get(object, n).equals(value)) {
                    return true;
                }
            }
        }
        // value not contained in object
        return false;
    }

    /**
     * Returns the index within a string of the first occurrence of a specified
     * substring.
     *
     * @param value the string to be searched.
     * @param substring the value to search for within the string
     * @return the index of the first instance of substring, or {@code -1} if
     * not found.
     */
    public static int indexOf(String value, String substring) {
        return value != null && substring != null ? value.indexOf(substring) : -1;
    }

    /**
     * Joins an array of strings into a single string value, with a specified separator.
     *
     * @param separator the separator to place between joined elements.
     * @param values the array of strings to be joined. You can use the array() function to construct this argument.
     * @return the string containing the joined strings.
     */
    public static String join(String[] values, String separator) {
        return values != null ? StringUtil.join(separator, (Object[]) values) : null;
    }

    /**
     * Returns the first key found in a map that matches the specified regular
     * expression pattern, or {@code null} if no such match is found.
     *
     * @param map the map whose keys are to be searched.
     * @param pattern a string containing the regular expression pattern to match.
     * @return the first matching key, or {@code null} if no match found.
     */
    public static String keyMatch(Object map, String pattern) {
        if (map instanceof Map) {
            // avoid unnecessary proxying via duck typing
            Pattern p = null;
            try {
                // TODO: cache oft-used patterns?
                p = Pattern.compile(pattern);
            } catch (PatternSyntaxException pse) {
                // invalid pattern results in no match
                return null;
            }
            for (Object key : ((Map<?, ?>) map).keySet()) {
                if (key instanceof String && p.matcher((String) key).matches()) {
                    return (String) key;
                }
            }
        }
        // no match
        return null;
    }

    /**
     * Returns the number of items in a collection, or the number of characters
     * in a string.
     *
     * @param value the object whose length is to be determined.
     * @return the length of the object, or {@code 0} if length could not be
     * determined.
     */
    public static int length(Object value) {
        if (value == null) {
            return 0;
        } else if (value instanceof CharSequence) {
            return ((CharSequence) value).length();
        } else if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        } else if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        } else if (value instanceof Object[]) {
            // doesn't handle primitives (but is cheap)
            return ((Object[]) value).length;
        } else if (value.getClass().isArray()) {
            // handles primitives (slightly more expensive)
            return Array.getLength(value);
        }
        // no items
        return 0;
    }

    /**
     * Returns {@code true} if the string contains the specified regular
     * expression pattern.
     *
     * @param value
     *            the string to be searched.
     * @param pattern
     *            a string containing the regular expression pattern to find.
     * @return {@code true} if the string contains the specified regular
     *         expression pattern.
     */
    public static boolean matches(String value, String pattern) {
        Pattern compiledPattern;
        try {
            compiledPattern = Pattern.compile(pattern);
        } catch (PatternSyntaxException pse) {
            logger.warn("Ignoring incorrect pattern : {}", pattern, pse);
            return false;
        }
        return compiledPattern.matcher(value).find();
    }

    /**
     * Returns an array containing the matches of a regular expression pattern
     * against a string, or {@code null} if no match is found. The first element
     * of the array is the entire match, and each subsequent element correlates
     * to any capture group specified within the regular expression.
     *
     * @param value
     *            the string to be searched.
     * @param pattern
     *            a string containing the regular expression pattern to match.
     * @return an array of matches, or {@code null} if no match found.
     */
    public static String[] matchingGroups(String value, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(value);
            if (m.find()) {
                int count = m.groupCount();
                String[] matches = new String[count + 1];
                matches[0] = m.group(0);
                for (int n = 1; n <= count; n++) {
                    matches[n] = m.group(n);
                }
                return matches;
            }
        } catch (PatternSyntaxException pse) {
            logger.warn("Ignoring incorrect pattern : {}", pattern, pse);
        }
        return null;
    }

    /**
     * Splits a string into an array of substrings around matches of the given
     * regular expression.
     *
     * @param value
     *            the string to be split.
     * @param regex
     *            the regular expression to split substrings around.
     * @return the resulting array of split substrings.
     */
    public static String[] split(String value, String regex) {
        return value != null ? value.split(regex) : null;
    }

    /**
     * Converts all of the characters in a string to lower case.
     *
     * @param value the string whose characters are to be converted.
     * @return the string with characters converted to lower case.
     */
    public static String toLowerCase(String value) {
        return value != null ? value.toLowerCase() : null;
    }

    /**
     * Returns the string value of an arbitrary object.
     *
     * @param value
     *            the object whose string value is to be returned.
     * @return the string value of the object.
     */
    public static String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Converts all of the characters in a string to upper case.
     *
     * @param value the string whose characters are to be converted.
     * @return the string with characters converted to upper case.
     */
    public static String toUpperCase(String value) {
        return value != null ? value.toUpperCase() : null;
    }

    /**
     * Returns a copy of a string with leading and trailing whitespace omitted.
     *
     * @param value the string whose white space is to be omitted.
     * @return the string with leading and trailing white space omitted.
     */
    public static String trim(String value) {
        return value != null ? value.trim() : null;
    }

    /**
     * Returns the URL encoding of the provided string.
     *
     * @param value
     *            the string to be URL encoded, which may be {@code null}.
     * @return the URL encoding of the provided string, or {@code null} if
     *         {@code string} was {@code null}.
     */
    public static String urlEncode(String value) {
        return Uris.formEncodeParameterNameOrValue(value);
    }

    /**
     * Returns the URL decoding of the provided string.
     *
     * @param value
     *            the string to be URL decoded, which may be {@code null}.
     * @return the URL decoding of the provided string, or {@code null} if
     *         {@code string} was {@code null}.
     */
    public static String urlDecode(String value) {
        return Uris.formDecodeParameterNameOrValue(value);
    }

    /**
     * Encode the given String input into Base 64.
     *
     * @param value
     *            the string to be Base64 encoded, which may be {@code null}.
     * @return the Base64 encoding of the provided string, or {@code null} if
     *         {@code string} was {@code null}.
     */
    public static String encodeBase64(final String value) {
        if (value != null) {
            return Base64.encode(value.getBytes());
        }
        return null;
    }

    /**
     * Decode the given Base64 String input.
     *
     * @param value
     *            the string to be Base64 decoded, which may be {@code null}.
     * @return the decoding of the provided string, or {@code null} if
     *         {@code string} was {@code null} or if the input was not a Base64 valid input.
     */
    public static String decodeBase64(final String value) {
        if (value != null) {
            return new String(Base64.decode(value));
        }
        return null;
    }

    /**
     * Returns the content of the given file as a plain String.
     *
     * @param filename
     *         file to be read
     * @return the file content as a String or {@literal null} if here was an error (missing file, ...)
     */
    public static String read(final String filename) {
        try {
            return asString(new FileInputStream(new File(filename)), Charset.defaultCharset());
        } catch (IOException e) {
            logger.warn("An error occurred while reading the file {}", filename, e);
            return null;
        }
    }

    /**
     * Returns the content of the given file as a {@link Properties}.
     *
     * @param filename
     *         file to be read
     * @return the file content as {@link Properties} or {@literal null} if here was an error (missing file, ...)
     */
    public static Properties readProperties(final String filename) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(new File(filename));
            Properties properties = new Properties();
            properties.load(fis);
            return properties;
        } catch (IOException e) {
            logger.warn("An error occurred while reading the file {}", filename, e);
            return null;
        } finally {
            closeSilently(fis);
        }
    }

}
