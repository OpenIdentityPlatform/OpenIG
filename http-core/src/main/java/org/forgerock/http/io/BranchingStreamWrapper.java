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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.util.Factory;

/**
 * Wraps an standard input stream with a stream that can branch to perform
 * divergent reads. All divergence between branches is maintained in a temporary
 * buffer.
 * <p>
 * <strong>Note:</strong> This stream and any branches it creates are not safe
 * for use by multiple concurrent threads.
 */
final class BranchingStreamWrapper extends BranchingInputStream {

    /** A shared object by all branches of the same input stream. */
    private Trunk trunk;

    /** Points to this branch's parent. */
    private final BranchingStreamWrapper parent;

    /** This branch's position relative to the trunk buffer. */
    private int position;

    BranchingStreamWrapper(InputStream in, Factory<Buffer> bufferFactory) {
        if (in instanceof BranchingStreamWrapper) {
            // branch off of existing trunk
            BranchingStreamWrapper bsw = (BranchingStreamWrapper) in;
            parent = bsw;
            trunk = bsw.trunk;
            position = bsw.position;
        } else {
            // wrapping a non-wrapping stream; sprout a new trunk
            parent = null;
            trunk = new Trunk(in, bufferFactory);
        }
        trunk.branches.add(this);
    }

    @Override
    public BranchingStreamWrapper branch() throws IOException {
        notClosed();
        // constructor will branch
        return new BranchingStreamWrapper(this, null);
    }

    boolean isClosed() {
        return trunk == null;
    }

    @Override
    public BranchingStreamWrapper parent() {
        return parent;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        return (read(b, 0, 1) > 0 ? (b[0] & 0xff) : -1);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        notClosed();
        int n;
        // try reading from buffer first
        if ((n = readBuffer(b, off, len)) == 0) {
            // not buffered; cascade the call
            if ((n = trunk.in.read(b, off, len)) >= 0) {
                // write result to buffer if necessary
                writeBuffer(b, off, n);
            }
        } else if (n < len) {
            int q;
            // byte array large that buffered data; read from input stream to fill up buffer
            if ((q = trunk.in.read(b, off + n, len - n)) >= 0) {
                // write result to buffer if necessary
                writeBuffer(b, off + n, q);
                n += q;
            }
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n < 0) {
            return 0;
        }
        notClosed();
        if (trunk.buffer == null && trunk.branches.size() == 1) {
            // not buffering; safely cascade call
            return trunk.in.skip(n);
        }
        // stream nowhere, just to buffer (or unbuffer) the result skipped
        return IO.stream(this, IO.nullOutputStream(), (int) Math.min(Integer.MAX_VALUE, n));
    }

    @Override
    public int available() throws IOException {
        notClosed();
        if (trunk.buffer != null) {
            int length = trunk.buffer.length();
            if (position < length) {
                // this branch is still reading from buffer
                // report buffer availability
                return length - position;
            }
        }
        return trunk.in.available();
    }

    @Override
    public void close() throws IOException {
        // multiple calls to close are harmless
        if (trunk != null) {
            try {
                closeBranches();
                trunk.branches.remove(this);
                // close buffer if applicable
                reviewBuffer();
                if (trunk.branches.size() == 0) {
                    // last one out turn off the lights
                    trunk.in.close();
                }
            } finally {
                // if all else fails, this branch thinks it's closed
                trunk = null;
            }
        }
    }

    private void closeBranches() throws IOException {
        // multiple calls are harmless
        if (trunk != null) {
            ArrayList<BranchingStreamWrapper> branches =
                    new ArrayList<BranchingStreamWrapper>(trunk.branches);
            for (BranchingStreamWrapper branch : branches) {
                if (branch.parent == this) {
                    // recursively closes its children
                    branch.close();
                }
            }
        }
    }

    /**
     * Closes this branching stream and all of the branches created from it.
     *
     * @throws Throwable
     *             may be raised by super.finalize().
     */
    @Override
    public void finalize() throws Throwable {
        try {
            close();
        } catch (IOException ioe) {
            // inappropriate to throw an exception when object is being collected
        }
        super.finalize();
    }

    /**
     * Closes the trunk buffer if there is no divergence between branches and
     * all remaining branch positions are outside the buffer.
     *
     * @throws IOException
     *             if an I/O exception occurs.
     */
    private void reviewBuffer() throws IOException {
        if (trunk.buffer == null) {
            // no buffer to review
            return;
        }
        int length = trunk.buffer.length();
        for (BranchingStreamWrapper branch : trunk.branches) {
            if (branch.position < length) {
                // branch is still using buffer; leave it alone
                return;
            }
        }
        // any remaining branches are non-divergent and outside buffer
        trunk.buffer.close();
        trunk.buffer = null;
    }

    /**
     * Throws an {@link IOException} if the stream is closed.
     */
    private void notClosed() throws IOException {
        if (trunk == null) {
            throw new IOException("stream is closed");
        }
    }

    private int readBuffer(byte[] b, int off, int len) throws IOException {
        int n = 0;
        if (trunk.buffer != null && trunk.buffer.length() > position) {
            n = trunk.buffer.read(position, b, off, len);
        }
        position += n;
        // see if the buffer can be closed after this operation
        reviewBuffer();
        return n;
    }

    private void writeBuffer(byte[] b, int off, int len) throws IOException {
        if (trunk.buffer == null && trunk.branches.size() > 1) {
            // diverging branches; allocate new buffer
            trunk.buffer = trunk.bufferFactory.newInstance();
            for (BranchingStreamWrapper branch : trunk.branches) {
                // set each branch position to beginning of new buffer
                branch.position = 0;
            }
        }
        if (trunk.buffer != null) {
            trunk.buffer.append(b, off, len);
            position += len;
        }
    }

    /** Object shared by all branches. */
    private final class Trunk {
        /** Keeps track of all branches on this trunk. */
        private final List<BranchingStreamWrapper> branches =
                new ArrayList<BranchingStreamWrapper>();

        /** The input stream being wrapped by the branches. */
        private final InputStream in;

        /** An object that creates new temporary buffers. */
        private final Factory<Buffer> bufferFactory;

        /**
         * A buffer to track diverging streams. Is {@code null} if there is no
         * divergence.
         */
        private Buffer buffer;

        private Trunk(InputStream in, Factory<Buffer> factory) {
            this.in = in;
            this.bufferFactory = factory;
        }
    }
}
