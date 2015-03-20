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

package org.forgerock.openig.filter.uma;

import static java.lang.String.format;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.util.JsonValues.getWithDeprecation;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.GenericFilter;
import org.forgerock.openig.filter.oauth2.BearerTokenExtractor;
import org.forgerock.openig.filter.oauth2.EnforcerFilter;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.filter.oauth2.challenge.InvalidRequestChallengeHandler;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Headers;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;

public class UmaResourceServerFilter extends GenericFilter {

    /**
     * Name of the realm when none is specified in the heaplet.
     */
    public static final String DEFAULT_REALM_NAME = "OpenIG";

    private final BearerTokenExtractor extractor;
    private final PermissionTicketCreator permissionTicketCreator;
    private final RptIntrospector introspector;
    private final Handler invalidRequest;
    private final String realm;
    private final String asUri;


    //For Demo
    private final String resourceSetId;
    private final Set<String> requiredScopes;


    //For Test
    private final Timer timer;

    public UmaResourceServerFilter(BearerTokenExtractor extractor, PermissionTicketCreator permissionTicketCreator,
            RptIntrospector introspector, String realm, String asUri, String resourceSetId,
            Set<String> requiredScopes, Timer timer) {
        this.extractor = extractor;
        this.permissionTicketCreator = permissionTicketCreator;
        this.introspector = introspector;
        this.invalidRequest = new InvalidRequestChallengeHandler(realm);
        this.realm = realm;
        this.asUri = asUri;

        //For demo
        this.resourceSetId = resourceSetId;
        this.requiredScopes = requiredScopes;

        this.timer = timer;
    }

    @Override
    public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
        try {
            timer.clear();
            timer.start("TOTAL");
            String rpt;
            try {
                timer.start("getRPT");
                rpt = getRPT(exchange.request);
            } catch (OAuth2TokenException e) {
                logger.debug("Multiple 'Authorization' headers in the request");
                logger.debug(e);
                invalidRequest.handle(exchange);
                timer.stop("TOTAL");
                return;
            } finally {
                timer.stop("getRPT");
            }

            // Check if request has RPT
            if (rpt == null || rpt.isEmpty()) {
                // If RPT not present - create permission request at AS
                timer.start("createPermissionRequest-NoRPT");
                createPermissionRequest(exchange);
                timer.stop("createPermissionRequest-NoRPT");
                timer.stop("TOTAL");
                return;
            }

            // If RPT present - introspect token at AS
            boolean introspect;
            try {
                timer.start("introspectingRPT");
                introspect = introspector.introspect(rpt);
            } catch (OAuth2TokenException e) {
                logger.debug(format("RPT could not be introspected"));
                logger.debug(e);
                invalidRequest.handle(exchange); //TODO not really correct
                timer.stop("TOTAL");
                return;
            } finally {
                timer.stop("introspectingRPT");
            }
            if (!introspect) {
                // If RPT invalid - create permission request at AS
                timer.start("createPermissionRequest-InvalidRPT");
                createPermissionRequest(exchange);
                timer.stop("createPermissionRequest-InvalidRPT");
                timer.stop("TOTAL");
                return;
            }
            // If RPT valid - allow through
            timer.start("next.handle");
            next.handle(exchange);
            timer.stop("next.handle");
            timer.stop("TOTAL");
        } finally {
            Map<String, Long> times = timer.getTimes();

            int a = 1;
        }
    }

    private String getRPT(Request request) throws OAuth2TokenException {
        Headers headers = request.getHeaders();
        List<String> authorizations = headers.get("Authorization");
        if (authorizations != null && authorizations.size() >= 2) {
            throw new OAuth2TokenException("Can't use more than 1 'Authorization' Header to convey"
                    + " the OAuth2 AccessToken");
        }
        String header = headers.getFirst("Authorization");
        return extractor.getAccessToken(header);
    }

    private void createPermissionRequest(Exchange exchange) throws IOException, HandlerException {
        String permissionTicket;
        try {
            permissionTicket = permissionTicketCreator.create(resourceSetId, requiredScopes);
        } catch (OAuth2TokenException e) {
            logger.debug(format("Permission Ticket could not be created"));
            logger.debug(e);
            invalidRequest.handle(exchange); //TODO not really correct
            return;
        }

        exchange.response = new Response();
        exchange.response.setStatus(403);
        exchange.response.getHeaders().put("WWW-Authenticate",
                Arrays.asList("UMA realm=\"" + realm + "\"", "as_uri=\"" + asUri + "\""));
        exchange.response.getHeaders().add("Content-Type", "application/json");
        exchange.response.setEntity(json(object(field("ticket", permissionTicket))).asMap());
    }

    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            Handler httpHandler =
                    heap.resolve(getWithDeprecation(config, logger, "providerHandler",
                            "httpHandler").required(), Handler.class);

            String clientId = config.get("clientId").required().asString();
            String clientSecret = config.get("clientSecret").required().asString();

            Timer timer = new Timer();

            DemoPatGetter patGetter = new DemoPatGetter(httpHandler,
                    config.get("accessTokenEndpoint").required().asString(),
                    clientId,
                    clientSecret,
                    config.get("username").required().asString(),
                    config.get("password").required().asString());

            PermissionTicketCreator permissionTicketCreator = new OpenAMPermissionTicketCreator(httpHandler,
                    config.get("permissionRequestEndpoint").required().asString(), patGetter, timer);

            RptIntrospector introspector = new OpenAMRptIntrospector(httpHandler,
                    config.get("tokenIntrospectionEndpoint").required().asString(), clientId, clientSecret);

            String realm = config.get("realm").defaultTo(DEFAULT_REALM_NAME).asString();

            String asUri = config.get("authorizationServerUri").required().asString();

            //For demo
            String resourceSetId;
            try {
                resourceSetId = new ResourceSetRegistrar(httpHandler,
                        config.get("resourceServerEndpoint").required().asString())
                        .getResourceSetId();
            } catch (OAuth2TokenException e) {
                throw new HeapException("Failed to get/register resource set", e);
            }
            Set<String> requiredScopes = config.get("requiredScopes").required().asSet(String.class);

            UmaResourceServerFilter filter = new UmaResourceServerFilter(new BearerTokenExtractor(),
                    permissionTicketCreator, introspector, realm, asUri, resourceSetId, requiredScopes, timer);

            if (getWithDeprecation(config, logger, "requireHttps", "enforceHttps").defaultTo(
                    Boolean.TRUE).asBoolean()) {
                try {
                    Expression<Boolean> expr = Expression.valueOf("${exchange.request.uri.scheme == 'https'}",
                            Boolean.class);
                    return new EnforcerFilter(expr, filter);
                } catch (ExpressionException e) {
                    // Can be ignored, since we completely control the expression
                }
            }

            return filter;
        }



        @Override
        public void destroy() {
            super.destroy();
        }
    }
}
