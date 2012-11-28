/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2012 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openig.filter;

// Java Standard Edition
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

// JSON Fluent
import org.forgerock.json.fluent.JsonValueException;

// OpenIG Core
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.LocationHeader;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.URIUtil;

/**
 * Specialised header filter that deals with rewriting Location headers on responses
 * that generate a redirect that would take the user directly to the application 
 * being proxied rather than via OpenIG.
 * 
 * <p><strong>Currently only HTTP 302 redirects are supported.</strong></p>
 *
 * @author Mark de Reeper
 */
public class RedirectFilter extends GenericFilter {

    /** The status code of a HTTP 302 Redirect. */
    public static final Integer REDIRECT_STATUS_302 = Integer.valueOf(302);

    /** The base URI of the OpenIG instance, used to rewrite Location headers. */
    public URI baseURI;

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {

        LogTimer timer = logger.getTimer().start();
        
        // We only care about responses so just call the next handler in the chain.
        next.handle(exchange);

        // Only process the response if it has a status that matches what we are looking for
        if (REDIRECT_STATUS_302.equals(exchange.response.status)) {
            processResponse(exchange.response);
        }

        timer.stop();
    }

    /**
     * Rewrite Location header if it would have the user go direct to the application.
     *
     * @param message the response message containing the Location header
     */
    private void processResponse(Message message) throws HandlerException {

        LocationHeader header = new LocationHeader(message);
        if (header.toString() != null) {
            try {
               URI currentURI = new URI(header.toString());
               URI rebasedURI = URIUtil.rebase(currentURI, baseURI);
               // Only rewite header if it has changed
               if (!currentURI.equals(rebasedURI)) {
                   message.headers.remove(LocationHeader.NAME);
                   message.headers.add(LocationHeader.NAME, rebasedURI.toString());
               }
            } catch (URISyntaxException ex) {
                throw logger.debug(new HandlerException(ex));
            }
        }
    }

    /** Creates and initialises a RedirectFilter in a heap environment. */
    public static class Heaplet extends NestedHeaplet {
        @Override public Object create() throws HeapException, JsonValueException {

            RedirectFilter filter = new RedirectFilter();
            filter.baseURI = config.get("baseURI").required().asURI(); // required

            return filter;
        }
    }
}
