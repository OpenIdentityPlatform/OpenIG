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

package org.forgerock.http.io;

import java.io.IOException;

/**
 * Wraps a byte array with a stream that can branch to perform divergent reads.
 */
final class ByteArrayBranchingStream extends BranchingInputStream {
    /** Branch that this was spawned from, or {@code null} if this is the trunk. */
    private ByteArrayBranchingStream parent = null;

    /** The index of the next byte to read from the byte array. */
    private int position = 0;

    /** The currently marked position in the stream. */
    private int mark = -1;

    /** The byte array to expose as the input stream. */
    private byte[] data;

    ByteArrayBranchingStream(byte[] data) {
        this.data = data;
    }

    @Override
    public ByteArrayBranchingStream branch() {
        ByteArrayBranchingStream branch = new ByteArrayBranchingStream(data);
        branch.position = this.position;
        branch.parent = this;
        return branch;
    }

    @Override
    public ByteArrayBranchingStream parent() {
        return parent;
    }

    @Override
    public synchronized int read() {
        return (position < data.length ? data[position++] & 0xff : -1);
    }

    @Override
    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (position >= data.length) {
            // end of stream has been reached
            return -1;
        }
        len = Math.min(len, data.length - position);
        System.arraycopy(data, position, b, off, len);
        position += len;
        return len;
    }

    @Override
    public synchronized long skip(long n) {
        if (n <= 0) {
            return 0;
        }
        n = Math.min(n, data.length - position);
        position += n;
        return n;
    }

    @Override
    public synchronized int available() {
        return data.length - position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readlimit) {
        mark = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark < 0) {
            throw new IOException("position was not marked");
        }
        position = mark;
    }

    @Override
    public void close() {
    }
}
