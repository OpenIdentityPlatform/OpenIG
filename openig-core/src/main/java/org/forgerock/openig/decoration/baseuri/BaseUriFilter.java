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

import java.io.IOException;
import java.net.URI;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.http.Exchange;

/**
 * BaseUriFilter overrides the existing request URI, making requests relative to
 * a new base URI.
 */
class BaseUriFilter implements Filter {

    private final Filter delegate;

    private final Expression<String> baseUri;

    /**
     * Creates a new base URI filter.
     *
     * @param delegate
     *            The delegated filter.
     * @param baseUri
     *            The new base URI to set.
     */
    BaseUriFilter(final Filter delegate, final Expression<String> baseUri) {
        this.delegate = delegate;
        this.baseUri = baseUri;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        if (exchange.request != null && exchange.request.getUri() != null) {
            exchange.request.getUri().rebase(URI.create(baseUri.eval(exchange)));
        }
        delegate.filter(exchange, next);
    }
}
