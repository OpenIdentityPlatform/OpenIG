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

package org.forgerock.openig.ui.record;

/**
 * Exception thrown by {@link RecordService} when something went wrong during records processing.
 */
class RecordException extends Exception {

    /**
     * Constructs a new exception with the specified detail {@code message}.
     * @param message detailed message
     */
    RecordException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail {@code message} and given {@code cause}.
     * @param message detailed message
     * @param cause cause
     */
    public RecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
