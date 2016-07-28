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

package org.forgerock.openig.handler.router;

import static java.lang.String.format;
import static org.forgerock.json.resource.ResourceException.BAD_REQUEST;
import static org.forgerock.json.resource.ResourceException.CONFLICT;
import static org.forgerock.json.resource.ResourceException.INTERNAL_ERROR;
import static org.forgerock.json.resource.ResourceException.NOT_FOUND;
import static org.forgerock.json.resource.ResourceException.NOT_SUPPORTED;
import static org.forgerock.json.resource.ResourceException.newResourceException;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.openig.handler.router.Route.routeName;

import java.util.Collection;

import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.SynchronousRequestHandler;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the CREST Endpoint to manage a route from the collection of routes deployed handled by
 * a {@link RouterHandler}.
 */
class RoutesItemRequestHandler implements SynchronousRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoutesItemRequestHandler.class);
    static final String ROUTE_ID = "routeId";

    private final RouterHandler routerHandler;

    RoutesItemRequestHandler(RouterHandler routerHandler) {
        this.routerHandler = routerHandler;
    }

    @Override
    public ActionResponse handleAction(Context context, ActionRequest actionRequest) throws ResourceException {
        throw newResourceException(NOT_SUPPORTED);
    }

    @Override
    public ResourceResponse handleCreate(Context context, CreateRequest request) throws ResourceException {
        String routeId = expectRouteId(context);
        try {
            JsonValue routeConfig = request.getContent();
            routerHandler.deploy(routeId, routeName(routeConfig, routeId), routeConfig.copy());
            return routeResourceResponse(routeId, routeConfig);
        } catch (RouterHandlerException e) {
            throw newResourceException(CONFLICT, "Unable to create the route", e);
        } catch (Exception e) {
            logger.error("", e);
            throw newResourceException(INTERNAL_ERROR, "An error occurred while trying to create a route", e)
                    .includeCauseInJsonValue();
        }
    }

    @Override
    public ResourceResponse handleDelete(Context context, DeleteRequest request) throws ResourceException {
        String routeId = expectRouteId(context);
        try {
            JsonValue routeConfig = routerHandler.undeploy(routeId);
            return routeResourceResponse(routeId, routeConfig);
        } catch (RouterHandlerException e) {
            throw newResourceException(NOT_FOUND, format("No route with id %s found", routeId));
        } catch (Exception e) {
            logger.error("", e);
            throw newResourceException(INTERNAL_ERROR,
                                        format("An error occurred while trying to delete the route with id '%s'",
                                               routeId),
                                        e)
                    .includeCauseInJsonValue();
        }
    }

    @Override
    public ResourceResponse handlePatch(Context context, PatchRequest patchRequest) throws ResourceException {
        throw newResourceException(NOT_SUPPORTED);
    }

    @Override
    public QueryResponse handleQuery(Context context,
                                     QueryRequest queryRequest,
                                     Collection<ResourceResponse> collection) throws ResourceException {
        throw newResourceException(NOT_SUPPORTED);
    }

    @Override
    public ResourceResponse handleRead(Context context, ReadRequest request) throws ResourceException {
        String routeId = expectRouteId(context);
        try {
            JsonValue routeConfig = routerHandler.routeConfig(routeId);
            return routeResourceResponse(routeId, routeConfig);
        } catch (RouterHandlerException e) {
            throw newResourceException(NOT_FOUND, format("No route with id %s found", routeId));
        } catch (Exception e) {
            logger.error("", e);
            throw newResourceException(INTERNAL_ERROR,
                                       format("An error occurred while trying to update the route with '%s'",
                                              routeId),
                                       e)
                    .includeCauseInJsonValue();
        }
    }

    @Override
    public ResourceResponse handleUpdate(Context context, UpdateRequest request) throws ResourceException {
        String routeId = expectRouteId(context);
        try {
            JsonValue routeConfig = request.getContent();
            routerHandler.update(routeId, routeName(routeConfig, routeId), routeConfig.copy());
            return routeResourceResponse(routeId, routeConfig);
        } catch (RouterHandlerException e) {
            throw newResourceException(NOT_FOUND);
        } catch (Exception e) {
            logger.error("", e);
            throw newResourceException(INTERNAL_ERROR,
                                       format("An error occurred while trying to update the route with '%s'",
                                              routeId),
                                       e)
                    .includeCauseInJsonValue();
        }
    }

    private static String expectRouteId(Context context) throws ResourceException {
        String routeId = extractRouteIdFromContext(context);
        if (routeId == null) {
            throw newResourceException(BAD_REQUEST, "Expecting a route id as the last path parameter");
        }
        return routeId;
    }

    private static String extractRouteIdFromContext(Context context) {
        UriRouterContext uriRouterContext = context.asContext(UriRouterContext.class);
        return uriRouterContext.getUriTemplateVariables().get(ROUTE_ID);
    }

    private static ResourceResponse routeResourceResponse(String routeId, JsonValue routeConfig) {
        return newResourceResponse(routeId, null, routeConfig);
    }
}

