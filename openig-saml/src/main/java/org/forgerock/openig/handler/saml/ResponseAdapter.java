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

package org.forgerock.openig.handler.saml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Adapts a given {@link HttpServletResponse} to override {@link #getWriter()}.
 */
class ResponseAdapter extends HttpServletResponseWrapper {

    private final PrintWriter writer;

    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @param response delegated response
     * @throws IllegalArgumentException
     *         if the response is null
     * @throws IOException if can't obtain servlet's {@link java.io.OutputStream}
     */
    ResponseAdapter(final HttpServletResponse response) throws IOException {
        super(response);
        // mimic servlet behaviour
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(response.getOutputStream(),
                                                                                response.getCharacterEncoding())));
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return writer;
    }
}
