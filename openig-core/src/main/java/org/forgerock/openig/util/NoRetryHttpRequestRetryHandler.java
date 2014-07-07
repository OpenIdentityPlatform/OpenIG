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
 * Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.openig.util;

// Apache HttpComponents

import java.io.IOException;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.Logger;

/**
 * A very simple implementation that always returns false for every exception which effectively turns off any
 * request retries.
 */
public class NoRetryHttpRequestRetryHandler implements HttpRequestRetryHandler {

    Logger logger = null;

    /**
     * Constructs a new <strong>{@code NoRetryHttpRequestRetryHandler}</strong>.
     *
     * @param logger The {@code Logger} to use when logging the exception message in {@code retryRequest}
     */
    public NoRetryHttpRequestRetryHandler(Logger logger) {
        this.logger = logger;
    }

    /**
     * Log the IOException message (when logger at {@code LogLevel.DEBUG} level) and return false for every request.
     *
     * @param e The IOException that triggered this retryRequest
     * @param i The number of times this retryRequest has been called
     * @param httpContext The HttpContext for this retryRequest
     * @return boolean always false in this implementation
     */
    @Override
    public boolean retryRequest(IOException e, int i, HttpContext httpContext) {

        if (logger != null && logger.isLoggable(LogLevel.DEBUG)) {
            logger.debug("NoRetryHttpRequestRetryHandler.retryRequest: IOException message " + e.getMessage());
        }

        return false;
    }
}
