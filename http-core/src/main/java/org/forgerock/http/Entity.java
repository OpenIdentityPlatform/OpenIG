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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.http;

import static org.forgerock.http.util.Json.*;
import static org.forgerock.util.Utils.*;
import static org.forgerock.http.util.StandardCharsets.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.forgerock.http.header.ContentEncodingHeader;
import org.forgerock.http.header.ContentLengthHeader;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.io.BranchingInputStream;
import org.forgerock.http.io.IO;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * Message content. An entity wraps a BranchingInputStream and provides various
 * convenience methods for accessing the content, transparently handling content
 * encoding. The underlying input stream can be branched in order to perform
 * repeated reads of the data. This is achieved by either calling
 * {@link #push()}, {@link #newDecodedContentReader(Charset)}, or
 * {@link #newDecodedContentInputStream()}. The branch can then be closed by
 * calling {@link #pop} on the entity, or {@code close()} on the returned
 * {@link #newDecodedContentReader(Charset) BufferedReader} or
 * {@link #newDecodedContentInputStream() InputStream}. Calling {@link #close}
 * on the entity fully closes the input stream invaliding any branches in the
 * process.
 * <p>
 * Several convenience methods are provided for accessing the entity as either
 * {@link #getBytes() byte}, {@link #getString() string}, or {@link #getJson()
 * JSON} content.
 */
public final class Entity implements Closeable {

    /*
     * Implementation note: this class lazily creates the alternative string and
     * json representations. Updates to the json content, string content, bytes,
     * or input stream invalidates the other representations accordingly. The
     * setters cascade updates in the following order: setJson() -> setString()
     * -> setBytes() -> setRawInputStream().
     */

    /** The Content-Type used when setting the entity to JSON. */
    static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=UTF-8";

    /** Default content stream. */
    private static final BranchingInputStream EMPTY_STREAM = IO
            .newBranchingInputStream(new byte[0]);

    /** The encapsulating Message which may have content encoding headers. */
    private final Message message;

    /** The input stream from which all all branches are created. */
    private BranchingInputStream trunk;

    /** The most recently created branch. */
    private BranchingInputStream head;

    /** Cached and lazily created JSON representation of the entity. */
    private Object json;

    /** Cached and lazily created String representation of the entity. */
    private String string;

    Entity(final Message message) {
        this.message = message;
        setEmpty();
    }

    /**
     * Returns {@code true} if this entity may contain data.
     * @return {@code true} if this entity may contain data.
     */
    public boolean mayContainData() {
        // Used reference equality intentionally (not equalsTo())
        return trunk != EMPTY_STREAM;
    }

    /**
     * Mark this entity as being empty.
     */
    public void setEmpty() {
        setRawContentInputStream(EMPTY_STREAM);
    }

    /**
     * Closes all resources associated with this entity. Any open streams will
     * be closed, and the underlying content reset back to a zero length.
     */
    @Override
    public void close() {
        setEmpty();
    }

    /**
     * Copies the decoded content of this entity to the provided writer. After
     * the method returns it will no longer be possible to read data from this
     * entity. This method does not push or pop branches. It does, however,
     * decode the content according to the {@code Content-Encoding} header if it
     * is present in the message.
     *
     * @param out
     *            The destination writer.
     * @throws IOException
     *             If an IO error occurred while copying the decoded content.
     */
    public void copyDecodedContentTo(final OutputStream out) throws IOException {
        IO.stream(getDecodedInputStream(head), out);
        out.flush();
    }

    /**
     * Copies the decoded content of this entity to the provided writer. After
     * the method returns it will no longer be possible to read data from this
     * entity. This method does not push or pop branches. It does, however,
     * decode the content according to the {@code Content-Encoding} and
     * {@code Content-Type} headers if they are present in the message.
     *
     * @param out
     *            The destination writer.
     * @throws IOException
     *             If an IO error occurred while copying the decoded content.
     */
    public void copyDecodedContentTo(final Writer out) throws IOException {
        IO.stream(getBufferedReader(head, null), out);
        out.flush();
    }

    /**
     * Copies the raw content of this entity to the provided output stream.
     * After the method returns it will no longer be possible to read data from
     * this entity. This method does not push or pop branches nor does it
     * perform any decoding of the raw data.
     *
     * @param out
     *            The destination output stream.
     * @throws IOException
     *             If an IO error occurred while copying the raw content.
     */
    public void copyRawContentTo(final OutputStream out) throws IOException {
        IO.stream(head, out);
        out.flush();
    }

    /**
     * Returns a byte array containing a copy of the decoded content of this
     * entity. Calling this method does not change the state of the underlying
     * input stream. Subsequent changes to the content of this entity will not
     * be reflected in the returned byte array, nor will changes in the returned
     * byte array be reflected in the content.
     *
     * @return A byte array containing a copy of the decoded content of this
     *         entity (never {@code null}).
     * @throws IOException
     *             If an IO error occurred while reading the content.
     */
    public byte[] getBytes() throws IOException {
        push();
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copyDecodedContentTo(bytes);
            return bytes.toByteArray();
        } finally {
            pop();
        }
    }

    /**
     * Returns the content of this entity decoded as a JSON object. Calling this
     * method does not change the state of the underlying input stream.
     * Subsequent changes to the content of this entity will not be reflected in
     * the returned JSON object, nor will changes in the returned JSON object be
     * reflected in the content.
     *
     * @return The content of this entity decoded as a JSON object, which will
     *         be {@code null} only if the content represents the JSON
     *         {@code null} value.
     * @throws IOException
     *             If an IO error occurred while reading the content or if the
     *             JSON is malformed.
     */
    public Object getJson() throws IOException {
        if (json == null) {
            final BufferedReader reader = newDecodedContentReader(UTF_8); // RFC 7159
            try {
                json = readJson(reader);
            } finally {
                reader.close();
            }
        }
        return json;
    }

    private static <T> T parse(ObjectMapper mapper, Reader reader) throws IOException { //TODO move JsonValueUtil/Json in to http-core util and use that
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
     * Returns an input stream representing the raw content of this entity.
     * Reading from the input stream will update the state of this entity.
     *
     * @return An input stream representing the raw content of this entity.
     */
    public InputStream getRawContentInputStream() {
        return head;
    }

    /**
     * Returns the content of this entity decoded as a string. Calling this
     * method does not change the state of the underlying input stream.
     * Subsequent changes to the content of this entity will not be reflected in
     * the returned string, nor will changes in the returned string be reflected
     * in the content.
     *
     * @return The content of this entity decoded as a string (never
     *         {@code null}).
     * @throws IOException
     *             If an IO error occurred while reading the content.
     */
    public String getString() throws IOException {
        if (string == null) {
            push();
            try {
                final StringWriter writer = new StringWriter();
                copyDecodedContentTo(writer);
                string = writer.toString();
            } finally {
                pop();
            }
        }
        return string;
    }

    /**
     * Returns a branched input stream representing the decoded content of this
     * entity. Reading from the returned input stream will NOT update the state
     * of this entity.
     * <p>
     * The entity will be decompressed based on any codings that are specified
     * in the {@code Content-Encoding} header.
     * <p>
     * <b>Note:</b> The caller is responsible for calling the input stream's
     * {@code close} method when it is finished reading the entity.
     *
     * @return A buffered input stream for reading the decoded entity.
     * @throws UnsupportedEncodingException
     *             If content encoding are not supported.
     * @throws IOException
     *             If an IO error occurred while reading the content.
     */
    public InputStream newDecodedContentInputStream() throws UnsupportedEncodingException,
            IOException {
        final BranchingInputStream headBranch = head.branch();
        try {
            return getDecodedInputStream(headBranch);
        } catch (final IOException e) {
            closeSilently(headBranch);
            throw e;
        }
    }

    /**
     * Returns a branched reader representing the decoded content of this
     * entity. Reading from the returned reader will NOT update the state of
     * this entity.
     * <p>
     * The entity will be decoded and/or decompressed based on any codings that
     * are specified in the {@code Content-Encoding} header.
     * <p>
     * If {@code charset} is not {@code null} then it will be used to decode the
     * entity, otherwise the character set specified in the message's
     * {@code Content-Type} header (if present) will be used, otherwise the
     * default {@code ISO-8859-1} character set.
     * <p>
     * <b>Note:</b> The caller is responsible for calling the reader's
     * {@code close} method when it is finished reading the entity.
     *
     * @param charset
     *            The character set to decode with, or message-specified or
     *            default if {@code null}.
     * @return A buffered reader for reading the decoded entity.
     * @throws UnsupportedEncodingException
     *             If content encoding or charset are not supported.
     * @throws IOException
     *             If an IO error occurred while reading the content.
     */
    public BufferedReader newDecodedContentReader(final Charset charset)
            throws UnsupportedEncodingException, IOException {
        final BranchingInputStream headBranch = head.branch();
        try {
            return getBufferedReader(headBranch, charset);
        } catch (final IOException e) {
            closeSilently(headBranch);
            throw e;
        }
    }

    /**
     * Restores the underlying input stream to the state it had immediately
     * before the last call to {@link #push}.
     */
    public void pop() {
        closeSilently(head);
        head = head.parent();
    }

    /**
     * Saves the current position of the underlying input stream and creates a
     * new buffered input stream. Subsequent attempts to read from this entity,
     * e.g. using {@link #copyRawContentTo(OutputStream) copyRawContentTo} or
     * {@code #getRawInputStream()}, will not impact the saved state.
     * <p>
     * Use the {@link #pop} method to restore the entity to the previous state
     * it had before this method was called.
     *
     * @throws IOException
     *             If this entity has been closed.
     */
    public void push() throws IOException {
        head = head.branch();
    }

    /**
     * Sets the content of this entity to the raw data contained in the provided
     * byte array. Calling this method will close any existing streams
     * associated with the entity. Also sets the {@code Content-Length} header,
     * overwriting any existing header.
     * <p>
     * Note: This method does not attempt to encode the entity based-on any
     * codings specified in the {@code Content-Encoding} header.
     *
     * @param value
     *            A byte array containing the raw data.
     */
    public void setBytes(final byte[] value) {
        if (value == null || value.length == 0) {
            message.getHeaders().putSingle(ContentLengthHeader.NAME, "0");
            setEmpty();
        } else {
            message.getHeaders()
                    .putSingle(ContentLengthHeader.NAME, Integer.toString(value.length));
            setRawContentInputStream(IO.newBranchingInputStream(value));
        }
    }

    /**
     * Sets the content of this entity to the JSON representation of the
     * provided object. Calling this method will close any existing streams
     * associated with the entity. Also sets the {@code Content-Type} and
     * {@code Content-Length} headers, overwriting any existing header.
     * <p>
     * Note: This method does not attempt to encode the entity based-on any
     * codings specified in the {@code Content-Encoding} header.
     *
     * @param value
     *            The object whose JSON representation is to be store in this
     *            entity.
     * @throws IOException
     *            If an IO error occurred while writing JSON, such as trying to
     *            output content in wrong context (non-matching end-array or
     *            end-object, for example).
     */
    public void setJson(final Object value) {
        message.getHeaders().putSingle(ContentTypeHeader.NAME, APPLICATION_JSON_CHARSET_UTF_8);
        try {
            setBytes(writeJson(value));
        } catch (IOException e) {
            // TODO do something better than a runtime exception :)
            throw new RuntimeException("Cannot produce JSON from " + value, e);
        }
        json = value;
    }

    /**
     * Sets the content of this entity to the provided input stream. Calling
     * this method will close any existing streams associated with the entity.
     * No headers will be set.
     *
     * @param is
     *            The input stream.
     */
    public void setRawContentInputStream(final BranchingInputStream is) {
        closeSilently(trunk); // Closes all sub-branches
        trunk = is != null ? is : EMPTY_STREAM;
        head = trunk;
        string = null;
        json = null;
    }

    /**
     * Sets the content of this entity to the provided string. Calling this
     * method will close any existing streams associated with the entity. Also
     * sets the {@code Content-Length} header, overwriting any existing header.
     * <p>
     * The character set specified in the message's {@code Content-Type} header
     * (if present) will be used, otherwise the default {@code ISO-8859-1}
     * character set.
     * <p>
     * Note: This method does not attempt to encode the entity based-on any
     * codings specified in the {@code Content-Encoding} header.
     *
     * @param value
     *            The string whose value is to be store in this entity.
     */
    public void setString(final String value) {
        setBytes(value != null ? value.getBytes(cs(null)) : null);
        string = value;
    }

    /**
     * Returns the content of this entity decoded as a string. Calling this
     * method does not change the state of the underlying input stream.
     *
     * @return The content of this entity decoded as a string (never
     *         {@code null}).
     */
    @Override
    public String toString() {
        try {
            return getString();
        } catch (final IOException e) {
            return e.getMessage();
        }
    }

    private Charset cs(final Charset charset) {
        if (charset != null) {
            return charset;
        }
        // use Content-Type charset if not explicitly specified
        final Charset contentType = ContentTypeHeader.valueOf(message).getCharset();
        if (contentType != null) {
            return contentType;
        }
        // use default per RFC 2616 if not resolved
        return ISO_8859_1;
    }

    private BufferedReader getBufferedReader(final InputStream is, final Charset charset)
            throws UnsupportedEncodingException, IOException {
        return new BufferedReader(new InputStreamReader(getDecodedInputStream(is), cs(charset)));
    }

    private InputStream getDecodedInputStream(final InputStream is)
            throws UnsupportedEncodingException, IOException {
        return ContentEncodingHeader.valueOf(message).decode(is);
    }
}
