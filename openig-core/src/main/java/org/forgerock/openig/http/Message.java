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

import org.forgerock.openig.io.BranchingInputStream;

/**
 * Elements common to requests and responses.
 *
 * @param <T>
 *            The sub-type of this message.
 */
public abstract class Message<T extends Message<T>> {

    /** Message entity body. */
    private BranchingInputStream entity;

    /** Message header fields. */
    private final Headers headers = new Headers();

    /** Protocol version. Default: {@code HTTP/1.1}. */
    private String version = "HTTP/1.1";

    Message() {
        // Hidden constructor.
    }

    /**
     * Returns the entity as an input stream.
     *
     * @return The entity as an input stream.
     */
    public final BranchingInputStream getEntity() {
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
     * Sets the entity as an input stream.
     *
     * @param entity
     *            The entity as an input stream.
     * @return This message.
     */
    public final T setEntity(final BranchingInputStream entity) {
        this.entity = entity;
        return getThis();
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
        return getThis();
    }

    abstract T getThis();
}
