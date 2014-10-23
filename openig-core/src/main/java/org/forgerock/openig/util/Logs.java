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

package org.forgerock.openig.util;

import static java.lang.String.format;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.Logger;

/**
 * Utilities methods related to log management.
 *
 * @see Logger
 */
public final class Logs {

    /**
     * Utility class.
     */
    private Logs() { }

    /**
     * Log the given {@code throwable} exception in the provided {@code logger} instance. This method will log (as
     * {@link LogLevel#ERROR} level) the localized messages of each chained exception (from the foremost throwable to
     * the deep-most caused by throwable). And then render the full exception's stack-trace (including caused by) at the
     * {@link LogLevel#DEBUG} level.
     * <p>
     * Output should looks like that:
     * <pre>
     *     {@code
     *     2014-10-23T12:07:45Z:_Router._Router:ERROR:The route defined in file '.../route.json' cannot be added
     *     2014-10-23T12:07:45Z:_Router._Router:ERROR:[            HeapException] > Unable to read well-known OpenID
     *                    Configuration from 'https://openam.example.com:8443/openam/.well-known/openid-configuration'
     *     2014-10-23T12:07:45Z:_Router._Router:ERROR:[ HttpHostConnectException] > Connection to
     *                    https://openam.example.com:8443 refused
     *     2014-10-23T12:07:45Z:_Router._Router:ERROR:[         ConnectException] > Connection refused}
     * </pre>
     *
     * @param logger
     *         where the messages will be logged
     * @param throwable
     *         the exception to be logged
     */
    public static void logDetailedException(final Logger logger, final Throwable throwable) {
        // Print each of the chained exception's messages (in order)
        Throwable current = throwable;
        while (current != null) {
            logger.error(format("[%25s] > %s",
                                current.getClass().getSimpleName(),
                                current.getLocalizedMessage()));
            current = current.getCause();
        }

        // Print the full stack trace (only visible when DEBUG is activated)
        // Had to render the exception myself otherwise the ConsoleLogSink/FileLogSink does not print the stack trace
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        logger.debug(writer.getBuffer().toString());
    }
}
