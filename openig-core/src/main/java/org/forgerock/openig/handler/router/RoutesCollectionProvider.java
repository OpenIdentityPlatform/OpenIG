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
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.openig.handler.router.Route.routeName;

import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConflictException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CREST handler used to manage the collection of routes deployed by a {@link RouterHandler}.
 */
class RoutesCollectionProvider implements CollectionResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(RoutesCollectionProvider.class);

    private final RouterHandler routerHandler;

    RoutesCollectionProvider(RouterHandler routerHandler) {
        this.routerHandler = routerHandler;
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(Context context, ActionRequest request) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(Context context,
                                                                     String resourceId,
                                                                     ActionRequest request) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(Context context, CreateRequest request) {
        String resourceId = request.getNewResourceId();
        if (resourceId == null) {
            return new BadRequestException("Missing resource-id as last path element").asPromise();
        }

        try {
            JsonValue routeConfig = request.getContent();
            routerHandler.deploy(resourceId,
                                 routeName(routeConfig, resourceId),
                                 routeConfig.copy());
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            return new ConflictException("Unable to create the route", e).asPromise();
        } catch (Exception e) {
            logger.error("An error occurred while trying to create route {}", resourceId, e);
            return new InternalServerErrorException("An error occurred while trying to create route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> deleteInstance(Context context,
                                                                       String resourceId,
                                                                       DeleteRequest request) {
        try {
            JsonValue routeConfig = routerHandler.undeploy(resourceId);
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            return new NotFoundException(format("No route with id %s found", resourceId), e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (Exception e) {
            logger.error("An error occurred while trying to delete route {}", resourceId, e);
            return new InternalServerErrorException("An error occurred while trying to delete route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(Context context,
                                                                      String resourceId,
                                                                      PatchRequest request) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(Context context,
                                                                     QueryRequest request,
                                                                     QueryResourceHandler handler) {
        return new NotSupportedException().asPromise();
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(Context context,
                                                                     String resourceId,
                                                                     ReadRequest request) {
        try {
            JsonValue routeConfig = routerHandler.routeConfig(resourceId);
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            return new NotFoundException(format("No route with id '%s' found", resourceId), e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (Exception e) {
            logger.error("An error occurred while trying to read route {}", resourceId, e);
            return new InternalServerErrorException("An error occurred while trying to read route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context,
                                                                       String resourceId,
                                                                       UpdateRequest request) {
        try {
            JsonValue routeConfig = request.getContent();
            routerHandler.update(resourceId, routeName(routeConfig, resourceId), routeConfig.copy());
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            return new NotFoundException(format("No route with id '%s' found", resourceId), e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (Exception e) {
            logger.error("An error occurred while trying to update route {}", resourceId, e);
            return new InternalServerErrorException("An error occurred while trying to update route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }


    private static ResourceResponse routeResourceResponse(String routeId, JsonValue routeConfig) {
        return newResourceResponse(routeId, null, routeConfig);
    }
}
