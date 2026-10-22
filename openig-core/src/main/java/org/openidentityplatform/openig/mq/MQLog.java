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
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.mq;

/**
 * Prepares the data received from a message broker for writing to the log.
 */
final class MQLog {

    private MQLog() {
    }

    /**
     * Returns the string form of a consumed message with its line breaks replaced by spaces: the
     * message comes from the broker and must not forge log lines.
     *
     * @param message the consumed message, its key or its body, may be {@code null}
     * @return the string form of the message without line breaks
     */
    static String forLog(Object message) {
        return String.valueOf(message).replaceAll("[\\r\\n]", " ");
    }
}
