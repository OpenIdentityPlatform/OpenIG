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
import static org.forgerock.json.resource.Responses.newQueryResponse;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.openig.handler.router.Route.routeName;
import static org.forgerock.util.query.QueryFilter.alwaysTrue;

import org.forgerock.api.annotations.ApiError;
import org.forgerock.api.annotations.CollectionProvider;
import org.forgerock.api.annotations.Create;
import org.forgerock.api.annotations.Delete;
import org.forgerock.api.annotations.Handler;
import org.forgerock.api.annotations.Operation;
import org.forgerock.api.annotations.Query;
import org.forgerock.api.annotations.Read;
import org.forgerock.api.annotations.Schema;
import org.forgerock.api.annotations.Update;
import org.forgerock.api.enums.CreateMode;
import org.forgerock.api.enums.QueryType;
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
@CollectionProvider(details = @Handler(id = "routes",
                                       resourceSchema = @Schema(schemaResource = "route.schema.json",
                                                                id = "route"),
                                       title = "i18n:#service.title",
                                       description = "i18n:#service.desc",
                                       mvccSupported = false))
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
    @Create(operationDescription = @Operation(description = "i18n:#create.desc",
                                              errors = { @ApiError(id = "BadRequest",
                                                                   code = 400,
                                                                   description = "i18n:#bad-request.desc"),
                                                         @ApiError(id = "Conflict",
                                                                   code = 409,
                                                                   description = "i18n:#conflict.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc") }),
            modes = CreateMode.ID_FROM_CLIENT)
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
            logger.error("An error occurred while trying to create route {}", resourceId, e);
            return new ConflictException("Unable to create route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (Exception e) {
            logger.error("An error occurred while trying to create route {}", resourceId, e);
            return new InternalServerErrorException("An error occurred while trying to create route " + resourceId, e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    @Delete(operationDescription = @Operation(description = "i18n:#delete.desc",
                                              errors = { @ApiError(id = "NotFound",
                                                                   code = 404,
                                                                   description = "i18n:#not-found.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc") }))
    public Promise<ResourceResponse, ResourceException> deleteInstance(Context context,
                                                                       String resourceId,
                                                                       DeleteRequest request) {
        try {
            JsonValue routeConfig = routerHandler.undeploy(resourceId);
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            logger.error("An error occurred while trying to delete route {}", resourceId, e);
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
    @Query(operationDescription = @Operation(description = "i18n:#query.desc",
                                             errors = { @ApiError(id = "NotSupported",
                                                                  code = 401,
                                                                  description = "i18n:#not-supported.desc") }),
           type = QueryType.FILTER)
    public Promise<QueryResponse, ResourceException> queryCollection(Context context,
                                                                     QueryRequest request,
                                                                     QueryResourceHandler resourceHandler) {
        // Reject queries with query ID, provided expressions and non "true" filter
        if (request.getQueryId() != null
                || request.getQueryExpression() != null
                || !alwaysTrue().equals(request.getQueryFilter())) {
            return new NotSupportedException("Only accept queries with filter=true").asPromise();
        }

        for (Route route : routerHandler.getRoutes()) {
            resourceHandler.handleResource(newResourceResponse(route.getId(), null, route.getConfig()));
        }

        return newQueryResponse().asPromise();
    }

    @Override
    @Read(operationDescription = @Operation(description = "i18n:#read.desc",
                                            errors = { @ApiError(id = "NotFound",
                                                                 code = 404,
                                                                 description = "i18n:#not-found.desc"),
                                                       @ApiError(id = "InternalServerError",
                                                                 code = 500,
                                                                 description = "i18n:#internal-server-error.desc") }))
    public Promise<ResourceResponse, ResourceException> readInstance(Context context,
                                                                     String resourceId,
                                                                     ReadRequest request) {
        try {
            JsonValue routeConfig = routerHandler.routeConfig(resourceId);
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            logger.error("An error occurred while trying to read route {}", resourceId, e);
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
    @Update(operationDescription = @Operation(description = "i18n:#update.desc",
                                              errors = { @ApiError(id = "NotFound",
                                                                   code = 404,
                                                                   description = "i18n:#not-found.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc") }))
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context,
                                                                       String resourceId,
                                                                       UpdateRequest request) {
        try {
            JsonValue routeConfig = request.getContent();
            routerHandler.update(resourceId, routeName(routeConfig, resourceId), routeConfig.copy());
            return routeResourceResponse(resourceId, routeConfig).asPromise();
        } catch (RouterHandlerException e) {
            logger.error("An error occurred while trying to update route {}", resourceId, e);
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
