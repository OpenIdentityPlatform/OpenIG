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

import org.forgerock.http.io.BranchingInputStream;

/**
 * Abstract message base class.
 *
 * @param <T>
 *            The sub-type of this message.
 */
abstract class MessageImpl<T extends MessageImpl<T>> implements Message {

    /** Message entity body. */
    private final Entity entity = new Entity(this);

    /** Message header fields. */
    private final Headers headers = new Headers();

    /** Protocol version. Default: {@code HTTP/1.1}. */
    private String version = "HTTP/1.1";

    MessageImpl() {
        // Hidden constructor.
    }

    @Override
    public void close() {
        entity.close();
    }

    @Override
    public final Entity getEntity() {
        return entity;
    }

    @Override
    public final Headers getHeaders() {
        return headers;
    }

    @Override
    public final String getVersion() {
        return version;
    }

    final void setEntity0(final Object o) {
        if (o instanceof BranchingInputStream) {
            entity.setRawContentInputStream((BranchingInputStream) o);
        } else if (o instanceof byte[]) {
            entity.setBytes((byte[]) o);
        } else if (o instanceof String) {
            entity.setString((String) o);
        } else {
            entity.setJson(o);
        }
    }

    final void setVersion0(final String version) {
        this.version = version;
    }
}
