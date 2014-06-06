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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.resource;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Provides a representation of an object. Reads and writes binary octet-streams, consistent
 * with whatever media type(s) and character set(s) the representation supports.
 */
public interface Representation {

    /**
     * Returns the MIME type of the representation. This method should support the media type
     * (and parameters, as appropriate) specified in of RFC 2616 §3.7.
     */
    String getContentType();

    /**
     * Returns the file extension that is used when storing the representation.
     */
    String getExtension();

    /**
     * Sets the media type that the representation should expect upon a call to its
     * {@code read} method. This method should support the media type (and parameters,
     * as appropriate) specified in RFC 2616 §3.7.
     *
     * @param type the media type that the representation will be requested to read.
     * @throws ResourceException if the representation cannot handle the specified media type.
     */
    void setContentType(String type) throws ResourceException;

    /**
     * Returns the content-length of the representation, or -1 if unknown.
     */
    int getLength();

    /**
     * Reads content from an input stream into the representation.
     *
     * @param in the input stream to read the content from.
     * @throws IOException if an I/O exception occurs.
     * @throws ResourceException if an exception occurs processing the representation.
     */
    void read(InputStream in) throws IOException, ResourceException;

    /**
     * Writes the representation to an output stream.
     *
     * @param out the output stream to write content to.
     * @throws IOException if an I/O exception occurs.
     * @throws ResourceException if an exception occurs processing the representation.
     */
    void write(OutputStream out) throws IOException, ResourceException;
}
