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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.http;

import java.io.IOException;

/**
 * An HTTP error.
 * <p>
 * TODO: re-use CREST ResourceException?
 */
public class ResponseException extends IOException {
    private static final long serialVersionUID = 7012424171155584261L;

    private final Response response;

    public ResponseException(int status) {
        this(new Response().setStatusAndReason(status));
    }

    public ResponseException(int status, String message) {
        this(new Response().setStatusAndReason(status), message);
    }

    public ResponseException(final Response response) {
        this(response, response.getReason());
    }

    public ResponseException(final Response response, String message) {
        this(response, message, null);
    }

    public ResponseException(final Response response, String message, final Throwable cause) {
        super(message, cause);
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }

}
