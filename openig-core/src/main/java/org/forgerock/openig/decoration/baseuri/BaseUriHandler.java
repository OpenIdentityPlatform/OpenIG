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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.decoration.baseuri;

import static java.lang.String.format;
import static org.forgerock.http.Responses.newInternalServerError;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.util.promise.Promises.newResultPromise;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.log.Logger;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * BaseUriHandler overrides the existing request URI, making requests relative to
 * a new base URI.
 */
class BaseUriHandler implements Handler {

    private final Handler delegate;

    private final Expression<String> baseUri;
    private final Logger logger;

    /**
     * Creates a new base URI handler.
     *
     * @param delegate
     *            The delegated handler.
     * @param baseUri
     *            The new base URI to set.
     * @param logger
     *            The logger to use with this handler.
     */
    BaseUriHandler(final Handler delegate, final Expression<String> baseUri, final Logger logger) {
        this.delegate = delegate;
        this.baseUri = baseUri;
        this.logger = logger;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        final String newBaseUri = baseUri.eval(bindings(context, request));
        if (newBaseUri == null) {
            logger.error(format("BaseUri Expression '%s' was evaluated to null",
                                baseUri));
            return newResultPromise(newInternalServerError());
        }
        if (request != null && request.getUri() != null) {
            try {
                request.getUri().rebase(new URI(newBaseUri));
            } catch (URISyntaxException e) {
                logger.error(format("Invalid baseUri '%s'", baseUri));
                return newResultPromise(newInternalServerError(e));
            }
        }
        return delegate.handle(context, request);
    }
}
