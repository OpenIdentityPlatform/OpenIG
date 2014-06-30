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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.text;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Allows records to be retrieved from a delimiter-separated file using key and value. Once
 * constructed, an instance of this class is thread-safe, meaning the object can be long-lived,
 * and multiple concurrent calls to {@link #getRecord(String, String) getRecord} is fully
 * supported.
 */
public class SeparatedValuesFile {

    /** The file containing the separated values to be read. */
    private final File file;

    /** The character set the file is encoded in. */
    private final Charset charset;

    /** The separator specification to split lines into fields. */
    private final Separator separator;

    /** Does the first line of the file contain the set of defined field keys. */
    private boolean header;

    /**
     * Explicit field keys in the order they appear in a record, overriding any existing field header,
     * or empty to use field header.
     */
    private final List<String> fields = new ArrayList<String>();

    /**
     * Builds a new SeparatedValuesFile reading the given {@code file} using a the {@link Separators#COMMA}
     * separator specification and {@code UTF-8} charset. This constructor consider the file has a header line.
     * <p>
     * It is equivalent to call:
     * <code> new SeparatedValuesFile(file, "UTF-8"); </code>
     *
     * @param file
     *         file to read from
     * @see #SeparatedValuesFile(File, Charset)
     */
    public SeparatedValuesFile(final File file) {
        this(file, Charset.forName("UTF-8"));
    }

    /**
     * Builds a new SeparatedValuesFile reading the given {@code file} using a the {@link Separators#COMMA}
     * separator specification. This constructor consider the file has a header line.
     * <p>
     * It is equivalent to call:
     * <code> new SeparatedValuesFile(file, charset, Separators.COMMA.getSeparator()); </code>
     *
     * @param file
     *         file to read from
     * @param charset
     *         {@link Charset} of the file (non-null)
     * @see #SeparatedValuesFile(File, Charset, Separator)
     */
    public SeparatedValuesFile(final File file, final Charset charset) {
        this(file, charset, Separators.COMMA.getSeparator());
    }

    /**
     * Builds a new SeparatedValuesFile reading the given {@code file}. This constructor consider the file has a header
     * line.
     * <p>
     * It is equivalent to call:
     * <code> new SeparatedValuesFile(file, charset, separator, true); </code>
     *
     * @param file
     *         file to read from
     * @param charset
     *         {@link Charset} of the file (non-null)
     * @param separator
     *         separator specification
     * @see #SeparatedValuesFile(File, Charset, Separator, boolean)
     */
    public SeparatedValuesFile(final File file, final Charset charset, final Separator separator) {
        this(file, charset, separator, true);
    }

    /**
     * Builds a new SeparatedValuesFile reading the given {@code file}.
     *
     * @param file
     *         file to read from
     * @param charset
     *         {@link Charset} of the file (non-null)
     * @param separator
     *         separator specification
     * @param header
     *         does the file has a header first line ?
     */
    public SeparatedValuesFile(final File file,
                               final Charset charset,
                               final Separator separator,
                               final boolean header) {
        this.file = file;
        this.charset = charset;
        this.separator = separator;
        this.header = header;
    }

    /**
     * Returns the explicit field keys in the order they appear in a record, overriding any existing field header,
     * or empty to use field header.
     * @return the explicit field keys in the order they appear in a record
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * Returns a record from the file where the specified key is equal to the specified value.
     *
     * @param key the key to use to lookup the record
     * @param value the value that the key should have to find a matching record.
     * @return the record with the matching value, or {@code null} if no such record could be found.
     * @throws IOException if an I/O exception occurs.
     */
    public Map<String, String> getRecord(String key, String value) throws IOException {
        Map<String, String> map = null;
        SeparatedValuesReader reader = new SeparatedValuesReader(
                new InputStreamReader(new FileInputStream(file), charset),
                separator
        );
        try {
            List<String> fields = this.fields;
            if (header) {
                // first line in the file is the field header
                List<String> record = reader.next();
                if (record != null && fields.size() == 0) {
                    // use header fields
                    fields = record;
                }
            }
            if (fields.size() > 0) {
                int index = fields.indexOf(key);
                if (index >= 0) {
                    // requested key exists
                    List<String> record;
                    while ((record = reader.next()) != null) {
                        if (record.get(index).equals(value)) {
                            map = new HashMap<String, String>(fields.size());
                            Iterator<String> fi = fields.iterator();
                            Iterator<String> ri = record.iterator();
                            while (fi.hasNext() && ri.hasNext()) {
                                // assign field-value pairs in map
                                map.put(fi.next(), ri.next());
                            }
                            break;
                        }
                    }
                }
            }
        } finally {
            reader.close();
        }
        return map;
    }
}
