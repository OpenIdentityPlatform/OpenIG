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
import static org.forgerock.json.fluent.JsonValue.field;
import static org.forgerock.json.fluent.JsonValue.json;
import static org.forgerock.json.fluent.JsonValue.object;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.forgerock.http.Context;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.filter.oauth2.BearerTokenExtractor;
import org.forgerock.openig.filter.oauth2.EnforcerFilter;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.filter.oauth2.challenge.InvalidRequestChallengeHandler;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

public class UmaResourceServerFilter extends GenericHeapObject implements Filter {

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

    public UmaResourceServerFilter(BearerTokenExtractor extractor,
                                   PermissionTicketCreator permissionTicketCreator,
                                   RptIntrospector introspector,
                                   String realm,
                                   String asUri,
                                   String resourceSetId,
                                   Set<String> requiredScopes) {
        this.extractor = extractor;
        this.permissionTicketCreator = permissionTicketCreator;
        this.introspector = introspector;
        this.invalidRequest = new InvalidRequestChallengeHandler(realm);
        this.realm = realm;
        this.asUri = asUri;

        //For demo
        this.resourceSetId = resourceSetId;
        this.requiredScopes = requiredScopes;
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

    private Promise<Response, NeverThrowsException> createPermissionRequest(Context context, Request request) {
        String permissionTicket;
        try {
            permissionTicket = permissionTicketCreator.create(resourceSetId, requiredScopes);
        } catch (OAuth2TokenException e) {
            logger.debug(format("Permission Ticket could not be created"));
            logger.debug(e);
            return invalidRequest.handle(context, request); //TODO not really correct
        }

        Response response = new Response(Status.FORBIDDEN);
        response.getHeaders().put("WWW-Authenticate",
                                  Arrays.asList("UMA realm=\"" + realm + "\"", "as_uri=\"" + asUri + "\""));
        response.getHeaders().add("Content-Type", "application/json");
        response.setEntity(json(object(field("ticket", permissionTicket))).asMap());
        return Promises.newResultPromise(response);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {
        String rpt;
        try {
            rpt = getRPT(request);
        } catch (OAuth2TokenException e) {
            logger.debug("Multiple 'Authorization' headers in the request");
            logger.debug(e);
            return invalidRequest.handle(context, request);
        }

        // Check if request has RPT
        if (rpt == null || rpt.isEmpty()) {
            // If RPT not present - create permission request at AS
            logger.debug("createPermissionRequest-NoRPT");
            return createPermissionRequest(context, request);
        }

        // If RPT present - introspect token at AS
        boolean introspect;
        try {
            logger.debug("introspectingRPT");
            introspect = introspector.introspect(rpt);
        } catch (OAuth2TokenException e) {
            logger.debug(format("RPT could not be introspected"));
            logger.debug(e);
            return invalidRequest.handle(context, request); //TODO not really correct
        }
        if (!introspect) {
            // If RPT invalid - create permission request at AS
            logger.debug("createPermissionRequest-InvalidRPT");
            return createPermissionRequest(context, request);
        }
        // If RPT valid - allow through
        logger.debug("next.handle");
        return next.handle(context, request);
    }

    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            Handler httpHandler = heap.resolve(config.get("providerHandler").required(), Handler.class);

            String clientId = config.get("clientId").required().asString();
            String clientSecret = config.get("clientSecret").required().asString();

            Timer timer = new Timer();

            DemoPatGetter patGetter = new DemoPatGetter(httpHandler,
                                                        config.get("accessTokenEndpoint").required().asURI(),
                                                        clientId,
                                                        clientSecret,
                                                        config.get("username").required().asString(),
                                                        config.get("password").required().asString());

            PermissionTicketCreator permissionTicketCreator =
                    new OpenAMPermissionTicketCreator(httpHandler,
                                                      config.get("permissionRequestEndpoint")
                                                            .required()
                                                            .asURI(),
                                                      patGetter,
                                                      timer);

            RptIntrospector introspector = new OpenAMRptIntrospector(httpHandler,
                                                                     config.get("tokenIntrospectionEndpoint")
                                                                           .required()
                                                                           .asURI(),
                                                                     clientId,
                                                                     clientSecret);

            String realm = config.get("realm").defaultTo(DEFAULT_REALM_NAME).asString();

            String asUri = config.get("authorizationServerUri").required().asString();

            //For demo
            String resourceSetId;
            try {
                resourceSetId = new ResourceSetRegistrar(httpHandler,
                                                         config.get("resourceServerEndpoint").required().asURI())
                        .getResourceSetId();
            } catch (OAuth2TokenException e) {
                throw new HeapException("Failed to get/register resource set", e);
            }
            Set<String> requiredScopes = config.get("requiredScopes").required().asSet(String.class);

            UmaResourceServerFilter filter = new UmaResourceServerFilter(new BearerTokenExtractor(),
                                                                         permissionTicketCreator,
                                                                         introspector,
                                                                         realm,
                                                                         asUri,
                                                                         resourceSetId,
                                                                         requiredScopes);

            if (config.get("requireHttps").defaultTo(Boolean.TRUE).asBoolean()) {
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
    }
}
