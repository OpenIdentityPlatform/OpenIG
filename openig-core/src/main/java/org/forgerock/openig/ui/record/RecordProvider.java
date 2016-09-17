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

package org.forgerock.openig.ui.record;

import static java.lang.String.format;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.query.QueryFilter.alwaysTrue;

import java.io.IOException;
import java.util.Set;

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
import org.forgerock.api.enums.Stability;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * CREST collection service dedicated to persist JSON objects (other types are not supported: arrays,
 * primitives, and null).
 *
 * <p>Usage example:
 *
 * <pre>
 *     {@code
 *     // Persists { "key" : [ 42 ] } and returns the server-created ID to use for future references
 *     requestHandler.handleCreate(context, newCreateRequest("record", json(object(field("key", array(42))))))
 *                   .then((response) -> {
 *                       System.out.println("Created resource with ID: " + response.getId());
 *                   });
 *     }
 * </pre>
 */
@CollectionProvider(details = @Handler(id = "record",
                                       resourceSchema = @Schema(schemaResource = "record.json", id = "record-type"),
                                       title = "i18n:#service.title",
                                       description = "i18n:#service.desc",
                                       mvccSupported = true))
public class RecordProvider implements CollectionResourceProvider {

    private final RecordService service;

    /**
     * Creates a new resource provider delegating to the given {@code service} for storage.
     * @param service storage service
     */
    public RecordProvider(RecordService service) {
        this.service = service;
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
                                              stability = Stability.INTERNAL,
                                              errors = { @ApiError(id = "BadRequest",
                                                                   code = 400,
                                                                   description = "i18n:#bad-request.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc") }),
            modes = CreateMode.ID_FROM_SERVER)
    public Promise<ResourceResponse, ResourceException> createInstance(Context context, CreateRequest request) {
        if (request.getNewResourceId() != null) {
            return new BadRequestException("Resource IDs are server-side provided only").asPromise();
        }

        try {
            Record record = service.create(request.getContent());
            return newResultPromise(newRecordResponse(record));
        } catch (IOException e) {
            return new InternalServerErrorException(e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    @Delete(operationDescription = @Operation(description = "i18n:#delete.desc",
                                              stability = Stability.INTERNAL,
                                              errors = { @ApiError(id = "NotFound",
                                                                   code = 404,
                                                                   description = "i18n:#not-found.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc"),
                                                         @ApiError(id = "PreconditionFailed",
                                                                   code = 412,
                                                                   description = "i18n:#precondition-failed.desc") }))
    public Promise<ResourceResponse, ResourceException> deleteInstance(Context context,
                                                                       String resourceId,
                                                                       DeleteRequest request) {
        try {
            Record record = service.delete(resourceId, request.getRevision());
            if (record == null) {
                return newNotFoundExceptionPromise(resourceId);
            }
            return newResultPromise(newRecordResponse(record));
        } catch (IOException e) {
            return new InternalServerErrorException("Cannot delete resource", e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (RecordException e) {
            return new PreconditionFailedException("Cannot delete resource", e)
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
                                             stability = Stability.INTERNAL,
                                             errors = { @ApiError(id = "NotSupported",
                                                                  code = 401,
                                                                  description = "i18n:#not-supported.desc"),
                                                        @ApiError(id = "InternalServerError",
                                                                  code = 500,
                                                                  description = "i18n:#internal-server-error.desc") }),
           type = QueryType.FILTER)
    public Promise<QueryResponse, ResourceException> queryCollection(Context context,
                                                                     QueryRequest request,
                                                                     QueryResourceHandler resourceHandler) {
        // Reject queries with query ID, provided expressions and non "true" filter
        if (request.getQueryId() != null
                || request.getQueryExpression() != null
                || !alwaysTrue().equals(request.getQueryFilter())) {
            return new NotSupportedException("Only accept queries with _queryFilter=true").asPromise();
        }

        try {
            Set<Record> instances = service.listAll();
            for (Record record : instances) {
                resourceHandler.handleResource(newRecordResponse(record));
            }
            return Responses.newQueryResponse().asPromise();
        } catch (IOException e) {
            return new InternalServerErrorException("Cannot list resources", e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    @Read(operationDescription = @Operation(description = "i18n:#read.desc",
                                            stability = Stability.INTERNAL,
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
            Record record = service.find(resourceId);
            if (record == null) {
                return newNotFoundExceptionPromise(resourceId);
            }
            return newResultPromise(newRecordResponse(record));
        } catch (IOException e) {
            return new InternalServerErrorException(e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    @Override
    @Update(operationDescription = @Operation(description = "i18n:#update.desc",
                                              stability = Stability.INTERNAL,
                                              errors = { @ApiError(id = "NotFound",
                                                                   code = 404,
                                                                   description = "i18n:#not-found.desc"),
                                                         @ApiError(id = "InternalServerError",
                                                                   code = 500,
                                                                   description = "i18n:#internal-server-error.desc"),
                                                         @ApiError(id = "PreconditionFailed",
                                                                   code = 412,
                                                                   description = "i18n:#precondition-failed.desc") }))
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context,
                                                                       String resourceId,
                                                                       UpdateRequest request) {
        try {
            Record record = service.update(resourceId, request.getRevision(), request.getContent());
            if (record == null) {
                return newNotFoundExceptionPromise(resourceId);
            }
            return newResultPromise(newRecordResponse(record));
        } catch (IOException e) {
            return new InternalServerErrorException(e)
                    .includeCauseInJsonValue()
                    .asPromise();
        } catch (RecordException e) {
            return new PreconditionFailedException("Cannot update resource", e)
                    .includeCauseInJsonValue()
                    .asPromise();
        }
    }

    private static ResourceResponse newRecordResponse(Record record) {
        return newResourceResponse(record.getId(),
                                   record.getRevision(),
                                   record.getContent());
    }

    private static Promise<ResourceResponse, ResourceException> newNotFoundExceptionPromise(String resourceId) {
        return new NotFoundException(format("Cannot find resource with id '%s'", resourceId))
                .asPromise();
    }
}
