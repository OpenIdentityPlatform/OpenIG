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

package org.forgerock.openig.uma;

import static java.lang.String.format;
import static org.forgerock.http.routing.RouteMatchers.requestUriMatcher;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.ResourceException.newBadRequestException;
import static org.forgerock.json.resource.ResourceException.newNotFoundException;
import static org.forgerock.json.resource.ResourceException.newNotSupportedException;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.query.QueryFilter.alwaysTrue;

import org.forgerock.http.Handler;
import org.forgerock.http.context.ServerContext;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/**
 * A {@link ShareCollectionProvider} is the CREST-based endpoint responsible for managing (creating, deleting, ...)
 * {@link Share} objects.
 *
 * <p>Supported operations: {@literal CREATE}, {@literal READ}, {@literal DELETE}
 * and {@literal QUERY} (simple shares list, no filtering).
 *
 * <pre>
 *     {@code {
 *         "type": "UmaRest",
 *         "config": {
 *           "umaService": "UmaService"
 *         }
 *       }
 *     }
 * </pre>
 */
public class ShareCollectionProvider implements CollectionResourceProvider {

    private final UmaSharingService service;

    /**
     * Constructs a new CREST endpoint for managing {@linkplain Share shares}.
     *
     * @param service
     *         delegating service
     */
    public ShareCollectionProvider(final UmaSharingService service) {
        this.service = service;
    }

    @Override
    public Promise<Resource, ResourceException> createInstance(final ServerContext context,
                                                               final CreateRequest request) {
        if (request.getNewResourceId() != null) {
            return newExceptionPromise(newNotSupportedException("Only POST-style of instance creation are supported"));
        }
        String path = request.getContent().get("path").asString();
        String pat = request.getContent().get("pat").asString();
        return service.createShare(path, pat)
                      .then(new Function<Share, Resource, ResourceException>() {
                          @Override
                          public Resource apply(final Share share) throws ResourceException {
                              return new Resource(share.getId(), null, asJson(share));
                          }
                      }, new Function<UmaException, Resource, ResourceException>() {
                          @Override
                          public Resource apply(final UmaException exception) throws ResourceException {
                              throw newBadRequestException("Failed to create a share", exception);
                          }
                      });
    }

    private static JsonValue asJson(final Share share) {
        return json(object(field("id", share.getId()),
                           field("pattern", share.getPattern().pattern()),
                           field("user_access_policy_uri", share.getUserAccessPolicyUri()),
                           field("pat", share.getPAT()),
                           field("resource_set_id", share.getResourceSetId())));
    }

    @Override
    public Promise<Resource, ResourceException> deleteInstance(final ServerContext context,
                                                               final String resourceId,
                                                               final DeleteRequest request) {
        Share share = service.removeShare(resourceId);
        if (share == null) {
            return newExceptionPromise(newNotFoundException(format("Share %s is unknown", resourceId)));
        }
        return newResultPromise(new Resource(resourceId, null, asJson(share)));
    }

    @Override
    public Promise<Resource, ResourceException> patchInstance(final ServerContext context,
                                                              final String resourceId,
                                                              final PatchRequest request) {
        return newExceptionPromise(newNotSupportedException());
    }

    @Override
    public Promise<QueryResult, ResourceException> queryCollection(final ServerContext context,
                                                                   final QueryRequest request,
                                                                   final QueryResourceHandler handler) {

        // Reject queries with query ID, provided expressions and non "true" filter
        if (request.getQueryId() != null
                || request.getQueryExpression() != null
                || !alwaysTrue().equals(request.getQueryFilter())) {
            return newExceptionPromise(newNotSupportedException("Only accept queries with filter=true"));
        }

        for (Share share : service.listShares()) {
            handler.handleResource(new Resource(share.getId(), null, asJson(share)));
        }

        return newResultPromise(new QueryResult());
    }

    @Override
    public Promise<Resource, ResourceException> readInstance(final ServerContext context,
                                                             final String resourceId,
                                                             final ReadRequest request) {
        Share share = service.getShare(resourceId);
        return newResultPromise(new Resource(resourceId, null, asJson(share)));
    }

    @Override
    public Promise<Resource, ResourceException> updateInstance(final ServerContext context,
                                                               final String resourceId,
                                                               final UpdateRequest request) {
        return newExceptionPromise(newNotSupportedException());
    }

    @Override
    public Promise<JsonValue, ResourceException> actionCollection(final ServerContext context,
                                                                  final ActionRequest request) {
        return newExceptionPromise(newNotSupportedException());
    }

    @Override
    public Promise<JsonValue, ResourceException> actionInstance(final ServerContext context,
                                                                final String resourceId,
                                                                final ActionRequest request) {
        return newExceptionPromise(newNotSupportedException());
    }

    /**
     * Creates and initializes an UMA share endpoint in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            UmaSharingService service = heap.resolve(config.get("umaService").required(), UmaSharingService.class);

            // Wrap it into the appropriate Crest classes
            ShareCollectionProvider provider = new ShareCollectionProvider(service);
            RequestHandler requestHandler = Resources.newCollection(provider);
            Handler crest = CrestHttp.newHttpHandler(requestHandler);

            // Finally use a router (that may disappear in the future)
            Router router = new Router();
            router.addRoute(requestUriMatcher(STARTS_WITH, "_openig/uma/share"), crest);

            return router;
        }
    }
}
