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

package org.forgerock.http;

/**
 * An exception that is thrown during a Http Application start up when the start up of the application fails.
 *
 * @since 1.0.0
 */
public class HttpApplicationException extends Exception {
    private static final long serialVersionUID = 3010033632180707412L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param  message The detail message.
     */
    public HttpApplicationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param  message The detail message.
     * @param  cause The exception which caused this exception to be thrown.
     */
    public HttpApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
