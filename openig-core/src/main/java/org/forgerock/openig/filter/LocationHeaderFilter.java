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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.openig.filter;

import static java.lang.String.format;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.util.JsonValues.asExpression;

import java.net.URI;
import java.net.URISyntaxException;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.ResponseException;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.util.Uris;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Responses;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * Rewrites Location headers on responses that generate a redirect that would
 * take the user directly to the application being proxied rather than taking
 * the user through OpenIG. Options:
 *
 * <pre>
 * {@code
 * {
 *   "baseURI"                     : expression        [OPTIONAL - default to the original URI
 *                                                                 of the request ]
 * }
 * }
 * </pre>
 *
 * Example:
 *
 * <pre>
 * {@code
 * {
 *      "name": "LocationRewriter",
 *      "type": "LocationHeaderFilter",
 *      "config": {
 *          "baseURI": "https://proxy.example.com:443/"
 *       }
 * }
 * }
 * </pre>
 */
public class LocationHeaderFilter extends GenericHeapObject implements Filter {

    /** The base URI of the OpenIG instance, used to rewrite Location headers. */
    private Expression<String> baseURI;

    /**
     * Sets the base URI used to rewrite Location headers.
     * @param baseURI expression that, when evaluated, will represents the base URI of this OpenIG instance
     */
    public void setBaseURI(final Expression<String> baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Rewrite Location header if it would have the user go directly to the application.
     *
     * @param response http message to be updated
     * @param bindings bindings to use when evaluating the new {@literal Location} value
     * @param originalUri request's original Uri
     */
    private Response processResponse(Response response, Bindings bindings, final URI originalUri) {
        LocationHeader header = LocationHeader.valueOf(response);
        if (header.getLocationUri() != null) {
            try {
                URI currentURI = new URI(header.getLocationUri());
                URI rebasedURI = Uris.rebase(currentURI, evaluateBaseUri(bindings, originalUri));
                // Only rewrite header if it has changed
                if (!currentURI.equals(rebasedURI)) {
                    response.getHeaders().put(LocationHeader.NAME, rebasedURI.toString());
                }
            } catch (URISyntaxException | ResponseException ex) {
                logger.error(ex);
                return Responses.newInternalServerError(ex);
            }
        }

        return response;
    }

    private URI evaluateBaseUri(final Bindings bindings, final URI originalUri)
            throws URISyntaxException, ResponseException {
        if (baseURI != null) {
            String uri = baseURI.eval(bindings);

            if (uri == null) {
                throw new ResponseException(format("The baseURI expression '%s' could not be resolved",
                                            baseURI.toString()));
            }
            return new URI(uri);
        } else {
            return originalUri;
        }
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        // We only care about responses so just call the next handler in the chain.
        return next.handle(context, request)
                   .then(new Function<Response, Response, NeverThrowsException>() {
                       @Override
                       public Response apply(final Response value) {
                           UriRouterContext routerContext = context.asContext(UriRouterContext.class);
                           return processResponse(value, bindings(context, request, value),
                                                  routerContext.getOriginalUri());
                       }
                   });
    }

    /** Creates and initializes a LocationHeaderFilter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {

            LocationHeaderFilter filter = new LocationHeaderFilter();
            filter.baseURI = asExpression(config.get("baseURI"), String.class);

            return filter;
        }
    }
}
