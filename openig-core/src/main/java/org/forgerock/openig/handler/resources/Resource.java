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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openig.handler.resources;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link Resource} represents any content that can be served through the {@link ResourceHandler}.
 */
public interface Resource {

    /**
     * Returns a new {@link InputStream} used to read resource's content.
     * Note that this is the responsibility of the caller to close the stream.
     * @return a new {@link InputStream}
     * @throws IOException if the stream cannot be opened
     */
    InputStream open() throws IOException;

    /**
     * Returns the media type of this resource.
     * @return the media type of this resource.
     */
    String getType();

    /**
     * Returns the timestamp when the resource has been last modified (expressed in
     * {@link java.util.concurrent.TimeUnit#MILLISECONDS}).
     * @return the timestamp when the resource has been last modified (ms)
     */
    long getLastModified();

    /**
     * Returns {@code true} if the resource has changed since the given timestamp (expressed in ms).
     * @param sinceTime timestamp to compare with this resource's last modified timestamp.
     * @return {@code true} if the resource has changed since the given timestamp (expressed in ms).
     */
    boolean hasChangedSince(long sinceTime);
}
