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
 * Copyright 2009 Sun Microsystems Inc.
 * Portions Copyright 2010–2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Miscellaneous string utility methods.
 */
public final class StringUtil {

    /** Platform specific end of line character. */
    private static final String EOL = System.getProperty("line.separator");

    /**
     * Static methods only.
     */
    private StringUtil() { }

    /**
     * Joins a collection of elements into a single string value, with a specified separator.
     *
     * @param separator the separator to place between joined elements.
     * @param elements the collection of strings to be joined.
     * @return the string containing the joined elements.
     */
    public static String join(String separator, Iterable<?> elements) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<?> i = elements.iterator(); i.hasNext();) {
            sb.append(i.next().toString());
            if (i.hasNext() && separator != null) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * Joins an array of strings into a single string value, with a specified separator.
     *
     * @param separator the separator to place between joined elements.
     * @param elements the array of strings to be joined.
     * @return the string containing the joined string array.
     */
    public static String join(String separator, Object... elements) {
        return join(separator, Arrays.asList(elements));
    }

    /**
     * Reads the provided input stream as a string and then closes the stream.
     *
     * @param is
     *            the input stream to be read.
     * @param charset
     *            the character set encoding of the input stream.
     * @return the content of the stream.
     * @throws IOException
     *             If an I/O error occurs.
     */
    public static String asString(final InputStream is, Charset charset) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset));
        try {
            final String firstLine = reader.readLine();
            if (firstLine == null) {
                return "";
            }
            final StringBuilder builder = new StringBuilder(firstLine);
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                builder.append(EOL);
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

}
