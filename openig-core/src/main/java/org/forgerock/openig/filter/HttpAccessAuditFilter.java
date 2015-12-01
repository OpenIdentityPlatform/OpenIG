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
package org.forgerock.openig.filter;

import static org.forgerock.audit.events.AccessAuditEventBuilder.accessEvent;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.ResourcePath.resourcePath;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.forgerock.audit.events.AccessAuditEventBuilder;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.services.context.ClientContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RequestAuditContext;
import org.forgerock.services.context.TransactionIdContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.time.TimeService;

/**
 * This filter aims to send some access audit events to the AuditService managed as a CREST handler.
 */
public class HttpAccessAuditFilter implements Filter {

    private final RequestHandler auditServiceHandler;
    private final TimeService time;

    /**
     * Constructs a new HttpAccessAuditFilter.
     *
     * @param auditServiceHandler The {@link RequestHandler} to publish the events.
     * @param time The {@link TimeService} to use.
     */
    public HttpAccessAuditFilter(RequestHandler auditServiceHandler, TimeService time) {
        this.auditServiceHandler = auditServiceHandler;
        this.time = time;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        TransactionIdContext txContext = context.asContext(TransactionIdContext.class);
        ClientContext clientContext = context.asContext(ClientContext.class);

        AccessAuditEventBuilder<?> accessAuditEventBuilder = accessEvent();

        accessAuditEventBuilder
                .eventName("OPENIG-HTTP-ACCESS")
                .timestamp(time.now())
                .transactionId(txContext.getTransactionId().getValue())
                .server(clientContext.getLocalAddress(),
                        clientContext.getLocalPort(),
                        clientContext.getLocalName())
                .client(clientContext.getRemoteAddress(),
                        clientContext.getRemotePort(),
                        clientContext.getRemoteHost())
                .httpRequest(clientContext.isSecure(),
                             request.getMethod(),
                             getRequestPath(getURI(context, request)),
                             new Form().fromRequestQuery(request),
                             request.getHeaders().copyAsMultiMapOfStrings());

        // We do not expect any RuntimeException as the downstream handler will have to take care
        // of that case themselves.
        return next.handle(context, request)
                .thenOnResult(onResult(context, accessAuditEventBuilder));
    }

    private static URI getURI(Context context, Request request) {
        if (context.containsContext(UriRouterContext.class)) {
            UriRouterContext uriRouterContext = context.asContext(UriRouterContext.class);
            return uriRouterContext.getOriginalUri();
        } else {
            return request.getUri().asURI();
        }
    }

    // See HttpContext.getRequestPath
    private static String getRequestPath(URI uri) {
        return new StringBuilder()
            .append(uri.getScheme())
            .append("://")
            .append(uri.getRawAuthority())
            .append(uri.getRawPath()).toString();
    }

    private ResultHandler<? super Response> onResult(final Context context,
                                                     final AccessAuditEventBuilder<?> accessAuditEventBuilder) {
        return new ResultHandler<Response>() {
            @Override
            public void handleResult(Response response) {
                sendAuditEvent(response, context, accessAuditEventBuilder);
            }

        };
    }

    private void sendAuditEvent(final Response response,
                                final Context context,
                                final AccessAuditEventBuilder<?> accessAuditEventBuilder) {
        RequestAuditContext requestAuditContext = context.asContext(RequestAuditContext.class);
        long elapsedTime = time.now() - requestAuditContext.getRequestReceivedTime();
        accessAuditEventBuilder.httpResponse(response.getHeaders().copyAsMultiMapOfStrings());
        accessAuditEventBuilder.response(mapResponseStatus(response.getStatus()),
                                         String.valueOf(response.getStatus().getCode()),
                                         elapsedTime,
                                         TimeUnit.MILLISECONDS);

        CreateRequest request = newCreateRequest(resourcePath("/access"), accessAuditEventBuilder.toEvent().getValue());
        auditServiceHandler.handleCreate(context, request);
    }

    private static AccessAuditEventBuilder.ResponseStatus mapResponseStatus(Status status) {
        switch(status.getFamily()) {
        case CLIENT_ERROR:
        case SERVER_ERROR:
            return AccessAuditEventBuilder.ResponseStatus.FAILED;
        default:
            return AccessAuditEventBuilder.ResponseStatus.SUCCESSFUL;
        }
    }
}
