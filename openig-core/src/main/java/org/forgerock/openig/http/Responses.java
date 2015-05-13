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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import static java.lang.String.format;

import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;

/**
 * Provide out-of-the-box, pre-configured {@link Response} objects.
 */
public final class Responses {

    /**
     * Empty private constructor for utility.
     */
    private Responses() { }

    /**
     * Generates an empty {@literal Internal Server Error} response ({@literal 500}).
     *
     * @return an empty {@literal Internal Server Error} response ({@literal 500}).
     */
    public static Response newInternalServerError() {
        return new Response(Status.INTERNAL_SERVER_ERROR);
    }

    /**
     * Generates an {@literal Internal Server Error} response ({@literal 500}) whose content is set to the given {@code
     * exception}'s message.
     *
     * @param exception
     *         wrapped exception
     * @return a configured {@literal Internal Server Error} response ({@literal 500}).
     */
    public static Response newInternalServerError(Exception exception) {
        return newInternalServerError(exception.getMessage())
                .setCause(exception);
    }

    /**
     * Generates an {@literal Internal Server Error} response ({@literal 500}) whose content is set to a concatenation
     * of the given {@code message} and {@code exception}'s message.
     *
     * @param message
     *         first part of the response's content
     * @param exception
     *         wrapped exception
     * @return a configured {@literal Internal Server Error} response ({@literal 500}).
     */
    public static Response newInternalServerError(String message, Exception exception) {
        return newInternalServerError(format("%s: %s", message, exception.getMessage())).setCause(exception);
    }

    /**
     * Generates an {@literal Internal Server Error} response ({@literal 500}) whose content is set to the given {@code
     * message}.
     *
     * @param message
     *         response's content
     * @return a configured {@literal Internal Server Error} response ({@literal 500}).
     */
    public static Response newInternalServerError(String message) {
        return newInternalServerError().setEntity(message);
    }

    /**
     * Generates an empty {@literal Not Found} response ({@literal 404}).
     *
     * @return an empty {@literal Not Found} response ({@literal 404}).
     */
    public static Response newNotFound() {
        return new Response(Status.NOT_FOUND);
    }

    /**
     * Generates a {@literal Not Found} response ({@literal 404}) whose content is set to the given {@code message}.
     *
     * @param message
     *         response's content
     * @return a configured {@literal Not Found} response ({@literal 404}).
     */
    public static Response newNotFound(String message) {
        return newNotFound().setEntity(message);
    }
}
