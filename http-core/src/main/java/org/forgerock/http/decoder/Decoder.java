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

package org.forgerock.http.decoder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.forgerock.http.util.CaseInsensitiveMap;
import org.forgerock.http.util.Indexed;
import org.forgerock.http.util.Loader;

/**
 * Decodes an HTTP message entity input stream.
 */
public interface Decoder extends Indexed<String> {

    /** Mapping of supported codings to associated decoders. */
    Map<String, Decoder> SERVICES = Collections.unmodifiableMap(new CaseInsensitiveMap<Decoder>(Loader.loadMap(
            String.class, Decoder.class)));

    /**
     * Returns the coding that the decoder supports, as it would appear in the {@code Content-Encoding} header.
     *
     * @return The coding that the decoder supports, as it would appear in the {@code Content-Encoding} header.
     */
    @Override
    String getKey();

    /**
     * Returns an instance of an input stream that decodes the specified input.
     *
     * @param in
     *            The input stream to be decoded.
     * @return an input stream exposing the decoded content.
     * @throws IOException
     *             If an I/O exception occurs during decoding.
     */
    InputStream decode(InputStream in) throws IOException;
}
