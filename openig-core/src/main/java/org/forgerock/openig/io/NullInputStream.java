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

package org.forgerock.openig.io;

import java.io.InputStream;

/**
 * An input stream that holds no data. GNDN.
 */
public class NullInputStream extends InputStream {

    /**
     * Unconditionally returns {@code -1} to indicate the end of stream has been reached.
     *
     * @return {@code -1} to indicate the end of stream has been reached.
     */
    @Override
    public int read() {
        // always at end of stream
        return -1;
    }

    /**
     * Unconditionally returns {@code -1} to indicate the end of stream has been reached.
     *
     * @param b
     *            The byte to read.
     * @return {@code -1} to indicate the end of stream has been reached.
     */
    @Override
    public int read(byte[] b) {
        return -1;
    }

    /**
     * Unconditionally returns {@code -1} to indicate the end of stream has been reached.
     *
     * @param b
     *            the buffer into which the data is read.
     * @param off
     *            the start offset in array b at which the data is written.
     * @param len
     *            the maximum number of bytes to read.
     * @return {@code -1} to indicate the end of stream has been reached.
     */
    @Override
    public int read(byte[] b, int off, int len) {
        return -1;
    }

    /**
     * Always returns 0 to indicate that no bytes were skipped.
     *
     * @param n
     *            the number of bytes to be skipped.
     * @return 0 to indicate that no bytes were skipped.
     */
    @Override
    public long skip(long n) {
        return 0;
    }

    /**
     * Always returns 0 to indicate that no bytes are available.
     *
     * @return 0 to indicate that no bytes are available.
     */
    @Override
    public int available() {
        return 0;
    }
}
