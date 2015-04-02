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
package org.forgerock.openig.audit;

import static org.forgerock.json.resource.Requests.*;
import static org.forgerock.openig.audit.Tag.*;

import java.util.Set;

import org.forgerock.audit.event.AccessAuditEventBuilder;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.Context;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.ServerInfo;
import org.forgerock.util.time.TimeService;

/**
 * Collects all the events and generate some events handled by commons-audit-framework.
 */
public class AuditEventCollector extends GenericHeapObject implements AuditEventListener {

    /**
     * The key for the tracking id of the exchange.
     */
    public static final String TRANSACTION_ID = "Access_TRACKING_ID";

    /**
     * The key for the start of the exchange (when it was handled by OpenIG.).
     */
    public static final String START = "Access_START";

    private ConnectionFactory factory;

    private TimeService time;

    /**
     * Create a new AuditEventCollector
     * @param factory the factory to use to route the created events.
     */
    public AuditEventCollector(TimeService time, ConnectionFactory factory) {
        this.time = time;
        this.factory = factory;
    }

    @Override
    public void onAuditEvent(org.forgerock.openig.audit.AuditEvent event) {
        final Exchange exchange = event.getExchange();

        // Extract the set of additional tags
        Set<String> tags = event.getTags();

        try {
            if (tags.contains(request.name())) {
                generateAccessLogRequest(exchange);
            }
            if (tags.contains(response.name())) {
                if (tags.contains(completed.name()) || tags.contains(exception.name())) {
                    generateAccessLogResponse(exchange);
                }
            }
        } catch (ResourceException e) {
            logger.error(e);
        }
    }


    private Resource generateAccessLogRequest(Exchange exchange) throws ResourceException {
        ServerInfo serverInfo = (ServerInfo) exchange.get("ServerInfo");
        AccessAuditEventBuilder<?> accessEventBuilder = AccessAuditEventBuilder.accessEvent();
        accessEventBuilder
            .transactionId((String) exchange.get(TRANSACTION_ID))
            .timestamp(time.now())
            .server(serverInfo.getIP(), serverInfo.getPort(), serverInfo.getHostname())
            .client(exchange.clientInfo.getRemoteAddress(),
                    exchange.clientInfo.getRemotePort(),
                    exchange.clientInfo.getRemoteHost())
            .http(exchange.request.getMethod(),
                  exchange.originalUri.getPath(),
                  exchange.originalUri.getQuery(),
                  exchange.request.getHeaders());

        return generateAuditEvent(accessEventBuilder.toEvent(), "access");
    }

    private Resource generateAccessLogResponse(Exchange exchange) throws ResourceException {
        AccessAuditEventBuilder<?> accessEventBuilder = AccessAuditEventBuilder.accessEvent();
        accessEventBuilder
            .transactionId((String) exchange.get(TRANSACTION_ID))
            .timestamp(time.now())
            .response(Integer.toString(exchange.response.getStatus()), time.since((Long) exchange.get(START)));

        return generateAuditEvent(accessEventBuilder.toEvent(), "access");
    }

    private Resource generateAuditEvent(org.forgerock.audit.event.AuditEvent auditEvent, String route)
            throws ResourceException {
        Connection connection = factory.getConnection();
        try {
            Context context = null;
            // TODO Use CREST facility to create the route's name
            return connection.create(context, newCreateRequest("/audit/" + route, auditEvent.getValue()));
        } finally {
            connection.close();
        }
    }


}

