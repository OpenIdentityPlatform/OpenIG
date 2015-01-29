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
 * Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static java.lang.String.*;
import static org.forgerock.openig.util.Json.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.LocationHeader;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.util.URIUtil;

/**
 * Rewrites Location headers on responses that generate a redirect that would
 * take the user directly to the application being proxied rather than taking
 * the user through OpenIG.
 */
public class LocationHeaderFilter extends GenericFilter {

    /** The base URI of the OpenIG instance, used to rewrite Location headers. */
    private Expression baseURI;

    /**
     * Sets the base URI used to rewrite Location headers.
     * @param baseURI expression that, when evaluated, will represents the base URI of this OpenIG instance
     */
    public void setBaseURI(final Expression baseURI) {
        this.baseURI = baseURI;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        // We only care about responses so just call the next handler in the chain.
        next.handle(exchange);

        processResponse(exchange);
    }

    /**
     * Rewrite Location header if it would have the user go directly to the application.
     *
     * @param exchange the exchange containing the response message containing the Location header
     */
    private void processResponse(Exchange exchange) throws HandlerException {
        Message<?> message = exchange.response;
        LocationHeader header = new LocationHeader(message);
        if (header.toString() != null) {
            try {
                URI currentURI = new URI(header.toString());
                URI rebasedURI = URIUtil.rebase(currentURI, evaluateBaseUri(exchange));
                // Only rewrite header if it has changed
                if (!currentURI.equals(rebasedURI)) {
                    message.getHeaders().remove(LocationHeader.NAME);
                    message.getHeaders().add(LocationHeader.NAME, rebasedURI.toString());
                }
            } catch (URISyntaxException ex) {
                throw logger.debug(new HandlerException(ex));
            }
        }
    }

    private URI evaluateBaseUri(final Exchange exchange) throws URISyntaxException, HandlerException {
        String uri = baseURI.eval(exchange, String.class);
        if (uri == null) {
            throw logger.debug(new HandlerException(format(
                    "The baseURI expression '%s' could not be resolved", baseURI.toString())));
        }
        return new URI(uri);
    }

    /** Creates and initializes a LocationHeaderFilter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {

            LocationHeaderFilter filter = new LocationHeaderFilter();
            filter.baseURI = asExpression(config.get("baseURI").required());

            return filter;
        }
    }
}
