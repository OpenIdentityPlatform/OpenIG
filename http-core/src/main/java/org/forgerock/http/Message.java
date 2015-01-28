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
 * Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.http;

import java.io.Closeable;

/**
 * Elements common to requests and responses.
 */
public interface Message extends Closeable {

    /**
     * Returns the entity.
     *
     * @return The entity.
     */
    Entity getEntity();

    /**
     * Returns the headers.
     *
     * @return The headers.
     */
    Headers getHeaders();

    /**
     * Returns the protocol version. Default: {@code HTTP/1.1}.
     *
     * @return The protocol version.
     */
    String getVersion();

    /**
     * Sets the content of the entity to the provided value. Calling this method
     * will close any existing streams associated with the entity. May also set
     * the {@code Content-Length} header, overwriting any existing header.
     * <p>
     * This method is intended mostly as a convenience method within scripts.
     * The parameter will be handled depending on its type as follows:
     * <ul>
     * <li>{@code BranchingInputStream} - equivalent to calling
     * {@link Entity#setRawContentInputStream}
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
    Message setEntity(Object o);

    /**
     * Sets the protocol version. Default: {@code HTTP/1.1}.
     *
     * @param version
     *            The protocol version.
     * @return This message.
     */
    Message setVersion(final String version);

    /**
     * Closes all resources associated with the entity. Any open streams will be
     * closed, and the underlying content reset back to a zero length.
     *
     * @see Entity#close()
     */
    @Override
    void close();
}
