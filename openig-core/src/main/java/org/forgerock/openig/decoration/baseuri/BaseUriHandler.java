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

package org.forgerock.openig.decoration.baseuri;

import java.net.URI;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.http.Exchange;
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

    /**
     * Creates a new base URI handler.
     *
     * @param delegate
     *            The delegated handler.
     * @param baseUri
     *            The new base URI to set.
     */
    BaseUriHandler(final Handler delegate, final Expression<String> baseUri) {
        this.delegate = delegate;
        this.baseUri = baseUri;
    }

    @Override
    public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
        Exchange exchange = context.asContext(Exchange.class);
        if (request != null && request.getUri() != null) {
            request.getUri().rebase(URI.create(baseUri.eval(exchange)));
        }
        return delegate.handle(context, request);
    }
}
