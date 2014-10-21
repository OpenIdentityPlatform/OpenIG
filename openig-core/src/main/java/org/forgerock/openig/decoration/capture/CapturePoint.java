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
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.decoration.capture;

/**
 * Specify where does the message capture takes place.
 */
public enum CapturePoint {
    /**
     * Prints all of the messages.
     */
    ALL,

    /**
     * Prints the filtered request (Filter only).
     */
    FILTERED_REQUEST,

    /**
     * Prints the filtered response (Filter only).
     */
    FILTERED_RESPONSE,

    /**
     * Prints input request.
     */
    REQUEST,

    /**
     * Prints the output response. In case of a filter, this represents the response produced by the next handler.
     * In case of a handler, this represents the handler's produced response object.
     */
    RESPONSE
}
