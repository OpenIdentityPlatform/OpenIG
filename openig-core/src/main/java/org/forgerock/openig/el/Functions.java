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
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.el;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.el.FunctionMapper;

import org.forgerock.openig.util.StringUtil;

/**
 * Maps between EL function names and methods. In this implementation all public
 * static methods are automatically exposed as functions.
 */
public class Functions extends FunctionMapper {

    /** A mapping of function names with methods to return. */
    private static final Map<String, Method> METHODS;
    static {
        METHODS = new HashMap<String, Method>();
        for (Method method : Functions.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                METHODS.put(method.getName(), method);
            }
        }
    }

    /**
     * Resolves the specified prefix and local name into a method. In this
     * implementation, the only supported supported prefix is none ({@code ""}).
     *
     * @param prefix the prefix of the function, or {@code ""} if no prefix.
     * @param localName the short name of the function.
     * @return the static method to invoke, or {@code null} if no match was
     * found.
     */
    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (prefix != null && localName != null && prefix.length() == 0) {
            return METHODS.get(localName);
        }
        // no match was found
        return null;
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
            return (object.toString().contains(value.toString()));
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
        return (value != null && substring != null ? value.indexOf(substring) : null);
    }

    /**
     * Joins an array of strings into a single string value, with a specified
     * separator.
     *
     * @param separator the separator to place between joined elements.
     * @param values the array of strings to be joined.
     * @return the string containing the joined strings.
     */
    public static String join(String[] values, String separator) {
        return (values != null ? StringUtil.join(separator, (Object[]) values) : null);
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
                if (key instanceof String) {
                    if (p.matcher((String) key).matches()) {
                        return (String) key;
                    }
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
        try {
            return Pattern.compile(pattern).matcher(value).find();
        } catch (PatternSyntaxException pse) {
            // ignore invalid pattern
        }
        return false;
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
            // ignore invalid pattern
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
        return (value != null ? value.split(regex) : null);
    }

    /**
     * Converts all of the characters in a string to lower case.
     *
     * @param value the string whose characters are to be converted.
     * @return the string with characters converted to lower case.
     */
    public static String toLowerCase(String value) {
        return (value != null ? value.toLowerCase() : null);
    }

    /**
     * Returns the string value of an arbitrary object.
     *
     * @param value
     *            the object whose string value is to be returned.
     * @return the string value of the object.
     */
    public static String toString(Object value) {
        return (value != null ? value.toString() : null);
    }

    /**
     * Converts all of the characters in a string to upper case.
     *
     * @param value the string whose characters are to be converted.
     * @return the string with characters converted to upper case.
     */
    public static String toUpperCase(String value) {
        return (value != null ? value.toUpperCase() : null);
    }

    /**
     * Returns a copy of a string with leading and trailing whitespace omitted.
     *
     * @param value the string whose white space is to be omitted.
     * @return the string with leading and trailing white space omitted.
     */
    public static String trim(String value) {
        return (value != null ? value.trim() : null);
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
        try {
            return value != null ? URLEncoder.encode(value, "UTF-8") : null;
        } catch (UnsupportedEncodingException e) {
            return value;
        }
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
        try {
            return value != null ? URLDecoder.decode(value, "UTF-8") : null;
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
