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

package org.forgerock.openig.io;

import java.io.IOException;

/**
 * An exception that is thrown if a buffer would overflow as a result of a write operation.
 */
public class OverflowException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with null as its detail message.
     */
    public OverflowException() {
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message
     *            The specified detail message.
     */
    public OverflowException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause
     *            The specified cause of this exception.
     */
    public OverflowException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message
     *            The specified detail message.
     * @param cause
     *            The specified cause of this exception.
     */
    public OverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
