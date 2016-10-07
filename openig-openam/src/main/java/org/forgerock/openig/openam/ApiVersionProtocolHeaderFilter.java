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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.openig.openam;

import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.AcceptApiVersionHeader;
import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.Version;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter overrides the protocol version in Accept-Api-Version header.
 * The protocol versions supported in OPENAM-13 is 1.0 and CREST adapter forces
 * to 2.0, throwing a 'Unsupported major version: 2.0' exception if not set.
 */
class ApiVersionProtocolHeaderFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ApiVersionProtocolHeaderFilter.class);

    private final Version protocolVersion;

    ApiVersionProtocolHeaderFilter(final Version protocolVersion) {
        this.protocolVersion = checkNotNull(protocolVersion, "The protocol version must be specified");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        try {
            final AcceptApiVersionHeader header = request.getHeaders().get(AcceptApiVersionHeader.class);
            request.getHeaders().put(new AcceptApiVersionHeader(protocolVersion, header.getResourceVersion()));
        } catch (final MalformedHeaderException e) {
            logger.error("Malformed '{}' header", AcceptApiVersionHeader.NAME, e);
            return newResponsePromise(newInternalServerError(e));
        }
        return next.handle(context, request);
    }
}
