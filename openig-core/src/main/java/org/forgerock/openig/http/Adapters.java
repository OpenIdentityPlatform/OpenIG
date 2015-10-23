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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.http;

import org.forgerock.http.protocol.Request;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;

/**
 * Adapters for converting between HTTP framework and legacy OpenIG APIs.
 *
 * FIXME: Temporary until OpenIG drops the Exchange.
 */
public final class Adapters {

    private Adapters() {
        // Prevent instantiation.
    }

    /**
     * Builds the exchange from the current context and request.
     *
     * @param context
     *            The context associated with a request currently being
     *            processed.
     * @param request
     *            The current request.
     * @return An HTTP exchange of request which can be used in legacy OpenIG.
     */
    public static Exchange asExchange(Context context, Request request) {
        final Exchange exchange = new Exchange(context, request.getUri().asURI());
        exchange.setClientContext(context.asContext(ClientContext.class));
        // TODO We will need to find a more robust solution when Exchange will be removed
        final AttributesContext attributesContext = context.asContext(AttributesContext.class);
        exchange.getAttributes().putAll(attributesContext.getAttributes());
        attributesContext.getAttributes().clear();
        return exchange;
    }
}
