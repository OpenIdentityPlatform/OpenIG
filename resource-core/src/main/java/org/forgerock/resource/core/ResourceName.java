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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.resource.core;

import static java.util.Arrays.asList;

import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * A relative path, or URL, to a resource. A resource name is an ordered list of
 * zero or more path elements in big-endian order. The string representation of
 * a resource name conforms to the URL path encoding rules defined in <a
 * href="http://tools.ietf.org/html/rfc3986#section-3.3">RFC 3986 section
 * 3.3</a>:
 *
 * <pre>
 * path          = path-abempty    ; begins with "/" or is empty
 *                 / ...
 *
 * path-abempty  = *( "/" segment )
 * segment       = *pchar
 * pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
 *
 * unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
 * pct-encoded   = "%" HEXDIG HEXDIG
 * sub-delims    = "!" / "$" / "&" / "'" / "(" / ")"
 *                 / "*" / "+" / "," / ";" / "="
 *
 * HEXDIG        =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
 * ALPHA         =  %x41-5A / %x61-7A   ; A-Z / a-z
 * DIGIT         =  %x30-39             ; 0-9
 * </pre>
 *
 * The empty resource name having zero path elements may be obtained by calling
 * {@link #empty()}. Resource names are case insensitive and empty path elements
 * are not allowed. In addition, resource names will be automatically trimmed
 * such that any leading or trailing slashes are removed. In other words, all
 * resource names will be considered to be "relative". At the moment the
 * relative path elements "." and ".." are not supported.
 * <p>
 * New resource names can be created from their string representation using
 * {@link #valueOf(String)}, or by deriving new resource names from existing
 * values, e.g. using {@link #parent()} or {@link #child(Object)}.
 * <p>
 * Example:
 *
 * <pre>
 * ResourceName base = ResourceName.valueOf(&quot;commons/rest&quot;);
 * ResourceName child = base.child(&quot;hello world&quot;);
 * child.toString(); // commons/rest/hello%20world
 *
 * ResourceName user = base.child(&quot;users&quot;).child(123);
 * user.toString(); // commons/rest/users/123
 * </pre>
 *
 * @since 1.0.0
 */
public final class ResourceName implements Comparable<ResourceName>, Iterable<String> {
    private static final ResourceName EMPTY = new ResourceName();

    /**
     * Non-safe characters are escaped as UTF-8 octets using "%" HEXDIG HEXDIG
     * production.
     */
    private static final char URL_ESCAPE_CHAR = '%';

    /**
     * Look up table for characters which do not need URL encoding.
     */
    private static final BitSet SAFE_URL_CHARS = new BitSet(128);
    static {
        /*
         * These characters do not need encoding.
         */
        for (char c : "-._~!$&'()*+,;=:@".toCharArray()) {
            SAFE_URL_CHARS.set(c);
        }

        /*
         * ASCII alphanumeric characters are ok as well.
         */
        SAFE_URL_CHARS.set('0', '9' + 1);
        SAFE_URL_CHARS.set('a', 'z' + 1);
        SAFE_URL_CHARS.set('A', 'Z' + 1);
    }

    /**
     * Returns the empty resource name whose string representation is the empty
     * string and which has zero path elements.
     *
     * @return The empty resource name.
     */
    public static ResourceName empty() {
        return EMPTY;
    }

    /**
     * Creates a new resource name using the provided name template and
     * unencoded path elements. This method first URL encodes each of the path
     * elements and then substitutes them into the template using
     * {@link String#format(String, Object...)}. Finally, the formatted string
     * is parsed as a resource name using {@link #valueOf(String)}.
     * <p>
     * This method may be useful in cases where the structure of a resource name
     * is not known at compile time, for example, it may be obtained from a
     * configuration file. Example usage:
     *
     * <pre>
     * String template = "rest/users/%s"
     * ResourceName name = ResourceName.format(template, &quot;bjensen&quot;);
     * </pre>
     *
     * @param template
     *            The resource name template.
     * @param pathElements
     *            The path elements to be URL encoded and then substituted into
     *            the template.
     * @return The formatted template parsed as a resource name.
     * @throws IllegalArgumentException
     *             If the formatted template contains empty path elements.
     * @see #urlEncode(Object)
     */
    public static ResourceName format(final String template, final Object... pathElements) {
        final String[] encodedPathElements = new String[pathElements.length];
        for (int i = 0; i < pathElements.length; i++) {
            encodedPathElements[i] = urlEncode(pathElements[i]);
        }
        return valueOf(String.format(template, (Object[]) encodedPathElements));
    }

    /**
     * Returns the URL path decoding of the provided object's string
     * representation.
     *
     * @param value
     *            The value to be URL path decoded.
     * @return The URL path decoding of the provided object's string
     *         representation.
     */
    public static String urlDecode(final Object value) {
        // First try fast-path decode of simple ASCII.
        final String s = value.toString();
        final int size = s.length();
        for (int i = 0; i < size; i++) {
            if (isUrlEscapeChar(s.charAt(i))) {
                // Slow path.
                return urlDecode0(s);
            }
        }
        return s;
    }

    /**
     * The UTF-8 character set which will be used for percent encoding.
     */
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static String urlDecode0(String s) {
        final StringBuilder builder = new StringBuilder(s.length());
        final int size = s.length();
        final byte[] buffer = new byte[size / 3];
        for (int i = 0; i < size;) {
            final char c = s.charAt(i);
            if (!isUrlEscapeChar(c)) {
                builder.append(c);
                i++;
            } else {
                int bufferPos = 0;
                for (; i < size && isUrlEscapeChar(s.charAt(i)); i += 3) {
                    if ((i + 2) >= size) {
                        throw new IllegalArgumentException(
                                "Path contains an incomplete percent encoding");
                    }
                    final String hexPair = s.substring(i + 1, i + 3);
                    try {
                        final int octet = Integer.parseInt(hexPair, 16);
                        if (octet < 0) {
                            throw new IllegalArgumentException(
                                    "Path contains an invalid percent encoding '" + hexPair + "'");
                        }
                        buffer[bufferPos++] = (byte) octet;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Path contains an invalid percent encoding '" + hexPair + "'");
                    }
                }
                builder.append(new String(buffer, 0, bufferPos, UTF8));
            }
        }
        return builder.toString();
    }

    /**
     * Returns the URL path encoding of the provided object's string
     * representation.
     *
     * @param value
     *            The value to be URL path encoded.
     * @return The URL path encoding of the provided object's string
     *         representation.
     */
    public static String urlEncode(final Object value) {
        // First try fast-path encode of simple ASCII.
        final String s = value.toString();
        final int size = s.length();
        for (int i = 0; i < size; i++) {
            final int c = s.charAt(i);
            if (!SAFE_URL_CHARS.get(c)) {
                // Slow path.
                return urlEncode0(s);
            }
        }
        return s;
    }

    /**
     * Fast lookup for encoding octets as hex.
     */
    private static final String[] byteToHex = new String[256];
    static {
        for (int i = 0; i < byteToHex.length; i++) {
            byteToHex[i] = String.format(Locale.ENGLISH, "%02X", i);
        }
    }

    private static String urlEncode0(String s) {
        final byte[] utf8 = s.getBytes(UTF8);
        final int size = utf8.length;
        final StringBuilder builder = new StringBuilder(size + 16);
        for (int i = 0; i < size; i++) {
            final int octet = utf8[i] & 0xff;
            if (SAFE_URL_CHARS.get(octet)) {
                builder.append((char) octet);
            } else {
                builder.append(URL_ESCAPE_CHAR);
                builder.append(byteToHex[octet]);
            }
        }
        return builder.toString();
    }

    /**
     * Compiled regular expression for splitting resource names into path
     * elements.
     */
    private static final Pattern PATH_SPLITTER = Pattern.compile("/");

    /**
     * Parses the provided string representation of a resource name.
     *
     * @param path
     *            The URL-encoded resource name to be parsed.
     * @return The provided string representation of a resource name.
     * @throws IllegalArgumentException
     *             If the resource name contains empty path elements.
     * @see #toString()
     */
    public static ResourceName valueOf(final String path) {
        if (path.isEmpty()) {
            // Fast-path.
            return EMPTY;
        }

        // Split on path separators and trim leading slash or trailing slash.
        final String[] elements = PATH_SPLITTER.split(path, -1);
        final int sz = elements.length;
        final int startIndex = elements[0].isEmpty() ? 1 : 0;
        final int endIndex = sz > 1 && elements[sz - 1].isEmpty() ? sz - 1 : sz;
        if (startIndex == endIndex) {
            return EMPTY;
        }

        // Normalize the path elements checking for empty elements.
        final StringBuilder trimmedPath = new StringBuilder(path.length());
        final StringBuilder normalizedPath = new StringBuilder(path.length());
        for (int i = startIndex; i < endIndex; i++) {
            final String element = elements[i];
            if (element.isEmpty()) {
                throw new IllegalArgumentException("Resource name '" + path
                        + "' contains empty path elements");
            }
            final String normalizedElement = normalizePathElement(element, true);
            if (i != startIndex) {
                trimmedPath.append('/');
                normalizedPath.append('/');
            }
            trimmedPath.append(element);
            normalizedPath.append(normalizedElement);
        }
        return new ResourceName(trimmedPath.toString(), normalizedPath.toString(), endIndex
                - startIndex);
    }

    private static boolean isUrlEscapeChar(final char c) {
        return c == URL_ESCAPE_CHAR;
    }

    private static String normalizePathElement(final String element, final boolean needsDecoding) {
        if (needsDecoding) {
            return urlEncode(urlDecode(element).toLowerCase(Locale.ENGLISH));
        } else {
            return element.toLowerCase(Locale.ENGLISH);
        }
    }

    private final String path; // uri encoded
    private final String normalizedPath; // uri encoded
    private final int size;

    /**
     * Creates a new empty resource name whose string representation is the
     * empty string and which has zero path elements. This method is provided in
     * order to comply with the Java Collections Framework recommendations.
     * However, it is recommended that applications use {@link #empty()} in
     * order to avoid unnecessary memory allocation.
     */
    public ResourceName() {
        this.path = this.normalizedPath = "";
        this.size = 0;
    }

    /**
     * Creates a new resource name having the provided path elements.
     *
     * @param pathElements
     *            The unencoded path elements.
     */
    public ResourceName(final Collection<? extends Object> pathElements) {
        int i = 0;
        final StringBuilder pathBuilder = new StringBuilder();
        final StringBuilder normalizedPathBuilder = new StringBuilder();
        for (final Object element : pathElements) {
            final String s = element.toString();
            if (i > 0) {
                pathBuilder.append('/');
                normalizedPathBuilder.append('/');
            }
            final String encodedPathElement = urlEncode(s);
            pathBuilder.append(encodedPathElement);
            final String normalizedPathElement = normalizePathElement(s, false);
            normalizedPathBuilder.append(urlEncode(normalizedPathElement));
            i++;
        }
        this.path = pathBuilder.toString();
        this.normalizedPath = normalizedPathBuilder.toString();
        this.size = pathElements.size();
    }

    /**
     * Creates a new resource name having the provided path elements.
     *
     * @param pathElements
     *            The unencoded path elements.
     */
    public ResourceName(final Object... pathElements) {
        this(asList(pathElements));
    }

    private ResourceName(final String path, final String normalizedPath, final int size) {
        this.path = path;
        this.normalizedPath = normalizedPath;
        this.size = size;
    }

    /**
     * Creates a new resource name which is a child of this resource name. The
     * returned resource name will have the same path elements as this resource
     * name and, in addition, the provided path element.
     *
     * @param pathElement
     *            The unencoded child path element.
     * @return A new resource name which is a child of this resource name.
     */
    public ResourceName child(final Object pathElement) {
        final String s = pathElement.toString();
        final String encodedPathElement = urlEncode(s);
        final String normalizedPathElement = normalizePathElement(s, false);
        final String normalizedEncodedPathElement = urlEncode(normalizedPathElement);
        if (isEmpty()) {
            return new ResourceName(encodedPathElement, normalizedEncodedPathElement, 1);
        } else {
            final String newPath = path + "/" + encodedPathElement;
            final String newNormalizedPath = normalizedPath + "/" + normalizedEncodedPathElement;
            return new ResourceName(newPath, newNormalizedPath, size + 1);
        }
    }

    /**
     * Compares this resource name with the provided resource name. Resource
     * names are compared case sensitively and ancestors sort before
     * descendants.
     *
     * @param o
     *            {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public int compareTo(final ResourceName o) {
        return normalizedPath.compareTo(o.normalizedPath);
    }

    /**
     * Creates a new resource name which is a descendant of this resource name.
     * The returned resource name will have be formed of the concatenation of
     * this resource name and the provided resource name.
     *
     * @param suffix
     *            The resource name to be appended to this resource name.
     * @return A new resource name which is a descendant of this resource name.
     */
    public ResourceName concat(final ResourceName suffix) {
        if (isEmpty()) {
            return suffix;
        } else if (suffix.isEmpty()) {
            return this;
        } else {
            final String newPath = path + "/" + suffix.path;
            final String newNormalizedPath = normalizedPath + "/" + suffix.normalizedPath;
            return new ResourceName(newPath, newNormalizedPath, size + suffix.size);
        }
    }

    /**
     * Creates a new resource name which is a descendant of this resource name.
     * The returned resource name will have be formed of the concatenation of
     * this resource name and the provided resource name.
     *
     * @param suffix
     *            The resource name to be appended to this resource name.
     * @return A new resource name which is a descendant of this resource name.
     * @throws IllegalArgumentException
     *             If the the suffix contains empty path elements.
     */
    public ResourceName concat(final String suffix) {
        return concat(valueOf(suffix));
    }

    /**
     * Returns {@code true} if {@code obj} is a resource name having the exact
     * same elements as this resource name.
     *
     * @param obj
     *            The object to be compared.
     * @return {@code true} if {@code obj} is a resource name having the exact
     *         same elements as this resource name.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ResourceName) {
            return normalizedPath.equals(((ResourceName) obj).normalizedPath);
        } else {
            return false;
        }
    }

    /**
     * Returns the path element at the specified position in this resource name.
     * The path element at position 0 is the top level element (closest to
     * root).
     *
     * @param index
     *            The index of the path element to be returned, where 0 is the
     *            top level element.
     * @return The path element at the specified position in this resource name.
     * @throws IndexOutOfBoundsException
     *             If the index is out of range (index < 0 || index >= size()).
     */
    public String get(final int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        int startIndex = 0;
        int endIndex = nextElementEndIndex(path, 0);
        for (int i = 0; i < index; i++) {
            startIndex = endIndex + 1;
            endIndex = nextElementEndIndex(path, startIndex);
        }
        return urlDecode(path.substring(startIndex, endIndex));
    }

    /**
     * Returns a hash code for this resource name.
     *
     * @return A hash code for this resource name.
     */
    @Override
    public int hashCode() {
        return normalizedPath.hashCode();
    }

    /**
     * Returns a resource name which is a subsequence of the path elements
     * contained in this resource name beginning with the first element (0) and
     * ending with the element at position {@code endIndex-1}. The returned
     * resource name will therefore have the size {@code endIndex}. Calling this
     * method is equivalent to:
     *
     * <pre>
     * subSequence(0, endIndex);
     * </pre>
     *
     * @param endIndex
     *            The end index, exclusive.
     * @return A resource name which is a subsequence of the path elements
     *         contained in this resource name.
     * @throws IndexOutOfBoundsException
     *             If {@code endIndex} is bigger than {@code size()}.
     */
    public ResourceName head(final int endIndex) {
        return subSequence(0, endIndex);
    }

    /**
     * Returns {@code true} if this resource name contains no path elements.
     *
     * @return {@code true} if this resource name contains no path elements.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns an iterator over the path elements in this resource name. The
     * returned iterator will not support the {@link Iterator#remove()} method
     * and will return path elements starting with index 0, then 1, then 2, etc.
     *
     * @return An iterator over the path elements in this resource name.
     */
    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            private int startIndex = 0;
            private int endIndex = nextElementEndIndex(path, 0);

            @Override
            public boolean hasNext() {
                return startIndex < path.length();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                final String element = path.substring(startIndex, endIndex);
                startIndex = endIndex + 1;
                endIndex = nextElementEndIndex(path, startIndex);
                return urlDecode(element);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * Returns the last path element in this resource name. Calling this method
     * is equivalent to:
     *
     * <pre>
     * resourceName.get(resourceName.size() - 1);
     * </pre>
     *
     * @return The last path element in this resource name.
     */
    public String leaf() {
        return get(size() - 1);
    }

    /**
     * Returns the resource name which is the immediate parent of this resource
     * name, or {@code null} if this resource name is empty.
     *
     * @return The resource name which is the immediate parent of this resource
     *         name, or {@code null} if this resource name is empty.
     */
    public ResourceName parent() {
        switch (size()) {
        case 0:
            return null;
        case 1:
            return EMPTY;
        default:
            final String newPath = path.substring(0, path.lastIndexOf('/') /* safe */);
            final String newNormalizedPath =
                    normalizedPath.substring(0, normalizedPath.lastIndexOf('/') /* safe */);
            return new ResourceName(newPath, newNormalizedPath, size - 1);
        }
    }

    /**
     * Returns the number of elements in this resource name, or 0 if it is
     * empty.
     *
     * @return The number of elements in this resource name, or 0 if it is
     *         empty.
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if this resource name is equal to or begins with the
     * provided resource resource name.
     *
     * @param prefix
     *            The resource name prefix.
     * @return {@code true} if this resource name is equal to or begins with the
     *         provided resource resource name.
     */
    public boolean startsWith(final ResourceName prefix) {
        if (size == prefix.size) {
            return equals(prefix);
        } else if (size < prefix.size) {
            return false;
        } else if (prefix.size == 0) {
            return true;
        } else {
            return normalizedPath.startsWith(prefix.normalizedPath)
                    && normalizedPath.charAt(prefix.normalizedPath.length()) == '/';
        }
    }

    /**
     * Returns {@code true} if this resource name is equal to or begins with the
     * provided resource resource name.
     *
     * @param prefix
     *            The resource name prefix.
     * @return {@code true} if this resource name is equal to or begins with the
     *         provided resource resource name.
     * @throws IllegalArgumentException
     *             If the the prefix contains empty path elements.
     */
    public boolean startsWith(final String prefix) {
        return startsWith(valueOf(prefix));
    }

    /**
     * Returns a resource name which is a subsequence of the path elements
     * contained in this resource name beginning with the element at position
     * {@code beginIndex} and ending with the element at position
     * {@code endIndex-1}. The returned resource name will therefore have the
     * size {@code endIndex - beginIndex}.
     *
     * @param beginIndex
     *            The beginning index, inclusive.
     * @param endIndex
     *            The end index, exclusive.
     * @return A resource name which is a subsequence of the path elements
     *         contained in this resource name.
     * @throws IndexOutOfBoundsException
     *             If {@code beginIndex} is negative, or {@code endIndex} is
     *             bigger than {@code size()}, or if {@code beginIndex} is
     *             bigger than {@code endIndex}.
     */
    public ResourceName subSequence(final int beginIndex, final int endIndex) {
        if (beginIndex < 0 || endIndex > size || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        if (beginIndex == 0 && endIndex == size) {
            return this;
        }
        if (endIndex - beginIndex == 0) {
            return EMPTY;
        }
        final String subPath = subPath(path, beginIndex, endIndex);
        final String subNormalizedPath = subPath(normalizedPath, beginIndex, endIndex);
        return new ResourceName(subPath, subNormalizedPath, endIndex - beginIndex);
    }

    /**
     * Returns a resource name which is a subsequence of the path elements
     * contained in this resource name beginning with the element at position
     * {@code beginIndex} and ending with the last element in this resource
     * name. The returned resource name will therefore have the size
     * {@code size() - beginIndex}. Calling this method is equivalent to:
     *
     * <pre>
     * subSequence(beginIndex, size());
     * </pre>
     *
     * @param beginIndex
     *            The beginning index, inclusive.
     * @return A resource name which is a subsequence of the path elements
     *         contained in this resource name.
     * @throws IndexOutOfBoundsException
     *             If {@code beginIndex} is negative, or if {@code beginIndex}
     *             is bigger than {@code size()}.
     */
    public ResourceName tail(final int beginIndex) {
        return subSequence(beginIndex, size);
    }

    /**
     * Returns the URL path encoded string representation of this resource name.
     *
     * @return The URL path encoded string representation of this resource name.
     * @see #valueOf(String)
     */
    @Override
    public String toString() {
        return path;
    }

    private int nextElementEndIndex(final String s, final int startIndex) {
        final int index = s.indexOf('/', startIndex);
        return index < 0 ? s.length() : index;
    }

    private String subPath(final String s, final int beginIndex, final int endIndex) {
        int startCharIndex = 0;
        int endCharIndex = nextElementEndIndex(s, 0);
        for (int i = 0; i < beginIndex; i++) {
            startCharIndex = endCharIndex + 1;
            endCharIndex = nextElementEndIndex(s, startCharIndex);
        }
        int tmpStartCharIndex;
        for (int i = beginIndex + 1; i < endIndex; i++) {
            tmpStartCharIndex = endCharIndex + 1;
            endCharIndex = nextElementEndIndex(s, tmpStartCharIndex);
        }
        return s.substring(startCharIndex, endCharIndex);
    }
}
