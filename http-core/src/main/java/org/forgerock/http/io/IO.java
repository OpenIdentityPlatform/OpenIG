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

package org.forgerock.http.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import org.forgerock.util.Factory;

/**
 * Utility class that can stream to and from streams.
 */
public final class IO {

    /**
     * 8 KiB.
     */
    public static final int DEFAULT_TMP_INIT_LENGTH = 8 * 1024;

    /**
     * 64 KiB.
     */
    public static final int DEFAULT_TMP_MEMORY_LIMIT = 64 * 1024;

    /**
     * 1 MiB.
     */
    public static final int DEFAULT_TMP_FILE_LIMIT = 1 * 1024 * 1024;

    /** Size of buffer to use during streaming. */
    private static final int BUF_SIZE = 8 * 1024;

    private static final InputStream NULL_INPUT_STREAM = new ByteArrayInputStream(new byte[0]);

    private static final OutputStream NULL_OUTPUT_STREAM = new OutputStream() {

        @Override
        public void write(final int b) throws IOException {
            // goes nowhere, does nothing
        }
    };

    /**
     * Creates a new branching input stream that wraps a byte array.
     *
     * @param bytes
     *            byte array to wrap with the branching input stream.
     * @return The branching input stream.
     */
    public static BranchingInputStream newBranchingInputStream(final byte[] bytes) {
        return new ByteArrayBranchingStream(bytes);
    }

    /**
     * Creates a new branching input stream to wrap another input stream. All
     * divergence between branches is maintained in a temporary buffer.
     * <p>
     * If the stream being wrapped is a branching input stream, this constructor
     * will simply branch off of that existing stream rather than wrapping it
     * with another branching input stream.
     * <p>
     * <strong>Note:</strong> This stream and any branches it creates are not
     * safe for use by multiple concurrent threads.
     *
     * @param in
     *            the stream to be wrapped.
     * @param bufferFactory
     *            an object that can create new temporary buffers (e.g. @link
     *            TemporaryStorage}).
     * @return The branching input stream.
     */
    public static BranchingInputStream newBranchingInputStream(final InputStream in,
            final Factory<Buffer> bufferFactory) {
        return new BranchingStreamWrapper(in, bufferFactory);
    }

    /**
     * Creates a new file buffer that uses a local file for data storage.
     * <p>
     * <strong>Note:</strong> The returned buffer is not synchronized. If
     * multiple threads access a buffer concurrently, threads that append to the
     * buffer should synchronize on the instance of this object.
     *
     * @param file
     *            the file to use as storage for the buffer.
     * @param limit
     *            the buffer length limit, after which an
     *            {@link OverflowException} will be thrown.
     * @return The file buffer.
     * @throws FileNotFoundException
     *             if the file cannot be created or opened for writing.
     * @throws SecurityException
     *             if a security manager denies access to the specified file.
     */
    public static Buffer newFileBuffer(final File file, final int limit)
            throws FileNotFoundException {
        return new FileBuffer(file, limit);
    }

    /**
     * Creates a new buffer that uses a byte array for data storage. The byte
     * array starts at a prescribed initial length, and grows exponentially up
     * to the prescribed limit.
     * <p>
     * <strong>Note:</strong> The returned buffer is not synchronized. If
     * multiple threads access a buffer concurrently, threads that append to the
     * buffer should synchronize on the instance of this object.
     *
     * @param initial
     *            the initial size of the byte array to create.
     * @param limit
     *            the buffer length limit, after which an
     *            {@link OverflowException} will be thrown.
     * @return The memory buffer.
     */
    public static Buffer newMemoryBuffer(final int initial, final int limit) {
        return new MemoryBuffer(initial, limit);
    }

    /**
     * Creates a new temporary buffer that first uses memory, then a temporary
     * file for data storage. Initially, a {@link #newMemoryBuffer(int, int)
     * memory} buffer is used; when the memory buffer limit is exceeded it
     * promotes to the use of a {@link #newFileBuffer(File, int) file} buffer.
     *
     * @param initialLength
     *            the initial length of memory buffer byte array.
     * @param memoryLimit
     *            the length limit of the memory buffer.
     * @param fileLimit
     *            the length limit of the file buffer.
     * @param directory
     *            the directory where temporary files are created, or
     *            {@code null} to use the system-dependent default temporary
     *            directory.
     * @return The temporary buffer.
     */
    public static Buffer newTemporaryBuffer(final int initialLength, final int memoryLimit,
            final int fileLimit, final File directory) {
        return new TemporaryBuffer(initialLength, memoryLimit, fileLimit, directory);
    }

    /**
     * Creates a new storage using the system dependent default temporary
     * directory and default sizes. Equivalent to call
     * {@code newTemporaryStorage(null)}.
     *
     * @return The temporary storage.
     */
    public static Factory<Buffer> newTemporaryStorage() {
        return newTemporaryStorage(null);
    }

    /**
     * Builds a storage using the given directory (may be {@literal null}) and
     * default sizes. Equivalent to call
     * {@code newTemporaryStorage(directory, HEIGHT_KB, SIXTY_FOUR_KB, ONE_MB)}
     * .
     *
     * @param directory
     *            The directory where temporary files are created. If
     *            {@code null}, then the system-dependent default temporary
     *            directory will be used.
     * @return The temporary storage.
     */
    public static Factory<Buffer> newTemporaryStorage(final File directory) {
        return newTemporaryStorage(directory, DEFAULT_TMP_INIT_LENGTH, DEFAULT_TMP_MEMORY_LIMIT,
                DEFAULT_TMP_FILE_LIMIT);
    }

    /**
     * Builds a storage using the given directory (may be {@literal null}) and
     * provided sizes.
     *
     * @param directory
     *            The directory where temporary files are created. If
     *            {@code null}, then the system-dependent default temporary
     *            directory will be used.
     * @param initialLength
     *            The initial length of memory buffer byte array.
     * @param memoryLimit
     *            The length limit of the memory buffer. Attempts to exceed this
     *            limit will result in promoting the buffer from a memory to a
     *            file buffer.
     * @param fileLimit
     *            The length limit of the file buffer. Attempts to exceed this
     *            limit will result in an {@link OverflowException} being
     *            thrown.
     * @return The temporary storage.
     */
    public static Factory<Buffer> newTemporaryStorage(final File directory,
            final int initialLength, final int memoryLimit, final int fileLimit) {
        return new Factory<Buffer>() {
            @Override
            public Buffer newInstance() {
                return newTemporaryBuffer(initialLength, memoryLimit, fileLimit, directory);
            }
        };
    }

    /**
     * Returns an input stream that holds no data.
     *
     * @return An input stream that holds no data.
     */
    public static InputStream nullInputStream() {
        return NULL_INPUT_STREAM;
    }

    /**
     * Returns an output stream that discards all data written to it.
     *
     * @return An output stream that discards all data written to it.
     */
    public static OutputStream nullOutputStream() {
        return NULL_OUTPUT_STREAM;
    }

    /**
     * Streams all data from an input stream to an output stream.
     *
     * @param in
     *            the input stream to stream the data from.
     * @param out
     *            the output stream to stream the data to.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static void stream(final InputStream in, final OutputStream out) throws IOException {
        final byte[] buf = new byte[BUF_SIZE];
        int n;
        while ((n = in.read(buf, 0, BUF_SIZE)) != -1) {
            out.write(buf, 0, n);
        }
    }

    /**
     * Streams data from an input stream to an output stream, up to a specified
     * length.
     *
     * @param in
     *            the input stream to stream the data from.
     * @param out
     *            the output stream to stream the data to.
     * @param len
     *            the number of bytes to stream.
     * @return the actual number of bytes streamed.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static int stream(final InputStream in, final OutputStream out, final int len)
            throws IOException {
        int remaining = len;
        final byte[] buf = new byte[BUF_SIZE];
        int n;
        while (remaining > 0 && (n = in.read(buf, 0, Math.min(remaining, BUF_SIZE))) >= 0) {
            out.write(buf, 0, n);
            remaining -= n;
        }
        return len - remaining;
    }

    /**
     * Streams all characters from a reader to a writer.
     *
     * @param in
     *            reader to stream the characters from.
     * @param out
     *            the writer to stream the characters to.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static void stream(final Reader in, final Writer out) throws IOException {
        final char[] buf = new char[BUF_SIZE];
        int n;
        while ((n = in.read(buf, 0, BUF_SIZE)) != -1) {
            out.write(buf, 0, n);
        }
    }

    /** Static methods only. */
    private IO() {
    }
}
