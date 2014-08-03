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

package org.forgerock.openig.http;

import java.io.Closeable;

import org.forgerock.openig.io.BranchingInputStream;

/**
 * Elements common to requests and responses.
 *
 * @param <T>
 *            The sub-type of this message.
 */
public abstract class Message<T extends Message<T>> implements Closeable {

    /** Message entity body. */
    private final Entity entity = new Entity(this);

    /** Message header fields. */
    private final Headers headers = new Headers();

    /** Protocol version. Default: {@code HTTP/1.1}. */
    private String version = "HTTP/1.1";

    Message() {
        // Hidden constructor.
    }

    /**
     * Returns the entity.
     *
     * @return The entity.
     */
    public final Entity getEntity() {
        return entity;
    }

    /**
     * Returns the headers.
     *
     * @return The headers.
     */
    public final Headers getHeaders() {
        return headers;
    }

    /**
     * Returns the protocol version. Default: {@code HTTP/1.1}.
     *
     * @return The protocol version.
     */
    public final String getVersion() {
        return version;
    }

    /**
     * Sets the content of the entity to the provided value. Calling this method
     * will close any existing streams associated with the entity. May also set
     * the {@code Content-Length} header, overwriting any existing header.
     * <p>
     * This method is intended mostly as a convenience method within scripts.
     * The parameter will be handled depending on its type as follows:
     * <ul>
     * <li>{@code BranchingInputStream} - equivalent to calling
     * {@link Entity#setRawInputStream}
     * <li>{@code byte[]} - equivalent to calling {@link Entity#setBytes}
     * <li>{@code String} - equivalent to calling {@link Entity#setString}
     * <li>{@code Object} - equivalent to calling {@link Entity#setJson}.
     * </ul>
     * <p>
     * Note: This method does not attempt to encode the entity based-on any
     * codings specified in the {@code Content-Encoding} header.
     *
     * @param o
     *            The object whose value should be stored in the entity.
     * @return This message.
     */
    public final T setEntity(Object o) {
        if (o instanceof BranchingInputStream) {
            entity.setRawInputStream((BranchingInputStream) o);
        } else if (o instanceof byte[]) {
            entity.setBytes((byte[]) o);
        } else if (o instanceof String) {
            entity.setString((String) o);
        } else {
            entity.setJson(o);
        }
        return thisMessage();
    }

    /**
     * Sets the protocol version. Default: {@code HTTP/1.1}.
     *
     * @param version
     *            The protocol version.
     * @return This message.
     */
    public final T setVersion(final String version) {
        this.version = version;
        return thisMessage();
    }

    /**
     * Closes all resources associated with the entity. Any open streams will be
     * closed, and the underlying content reset back to a zero length.
     *
     * @see Entity#close()
     */
    public void close() {
        entity.close();
    }

    abstract T thisMessage();
}
