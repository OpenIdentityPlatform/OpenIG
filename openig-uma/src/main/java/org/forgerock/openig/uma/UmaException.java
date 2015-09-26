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

package org.forgerock.openig.uma;

import org.forgerock.http.protocol.ResponseException;

/**
 * UMA Resource Server specific exception thrown when unrecoverable errors are happening.
 */
public class UmaException extends ResponseException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new UmaException with the given {@code message}.
     *
     * @param message
     *         cause description
     */
    public UmaException(final String message) {
        super(message);
    }

    /**
     * Creates a new UmaException with the given {@code message} and the given {@code cause}.
     *
     * @param message
     *         cause description
     * @param cause
     *         parent cause
     */
    public UmaException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
