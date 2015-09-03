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
import static org.forgerock.json.resource.ResourceException.BAD_REQUEST;
import static org.forgerock.json.resource.ResourceException.NOT_FOUND;
import static org.forgerock.json.resource.ResourceException.NOT_SUPPORTED;
import static org.forgerock.json.resource.ResourceException.getException;
import static org.forgerock.json.resource.Responses.newQueryResponse;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.query.QueryFilter.alwaysTrue;

import org.forgerock.http.Context;
import org.forgerock.http.Handler;
import org.forgerock.http.routing.Router;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
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
    public Promise<ResourceResponse, ResourceException> createInstance(final Context context,
                                                                       final CreateRequest request) {
        if (request.getNewResourceId() != null) {
            return getException(NOT_SUPPORTED,
                                "Only POST-style of instance creation are supported").asPromise();
        }
        String path = request.getContent().get("path").asString();
        String pat = request.getContent().get("pat").asString();
        return service.createShare(path, pat)
                      .then(new Function<Share, ResourceResponse, ResourceException>() {
                          @Override
                          public ResourceResponse apply(final Share share) throws ResourceException {
                              return newResourceResponse(share.getId(), null, asJson(share));
                          }
                      }, new Function<UmaException, ResourceResponse, ResourceException>() {
                          @Override
                          public ResourceResponse apply(final UmaException exception) throws ResourceException {
                              throw getException(BAD_REQUEST, "Failed to create a share", exception);
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
    public Promise<ResourceResponse, ResourceException> deleteInstance(final Context context,
                                                                       final String resourceId,
                                                                       final DeleteRequest request) {
        Share share = service.removeShare(resourceId);
        if (share == null) {
            return getException(NOT_FOUND,
                                format("Share %s is unknown", resourceId)).asPromise();
        }
        return newResultPromise(newResourceResponse(resourceId, null, asJson(share)));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(final Context context,
                                                                      final String resourceId,
                                                                      final PatchRequest request) {
        return getException(NOT_SUPPORTED).asPromise();
    }

    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(final Context context,
                                                                     final QueryRequest request,
                                                                     final QueryResourceHandler handler) {

        // Reject queries with query ID, provided expressions and non "true" filter
        if (request.getQueryId() != null
                || request.getQueryExpression() != null
                || !alwaysTrue().equals(request.getQueryFilter())) {
            return getException(NOT_SUPPORTED,
                                "Only accept queries with filter=true").asPromise();
        }

        for (Share share : service.listShares()) {
            handler.handleResource(newResourceResponse(share.getId(), null, asJson(share)));
        }

        return newResultPromise(newQueryResponse());
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(final Context context,
                                                                     final String resourceId,
                                                                     final ReadRequest request) {
        Share share = service.getShare(resourceId);
        return newResultPromise(newResourceResponse(resourceId, null, asJson(share)));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(final Context context,
                                                                       final String resourceId,
                                                                       final UpdateRequest request) {
        return getException(NOT_SUPPORTED).asPromise();
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(final Context context,
                                                                       final ActionRequest request) {
        return getException(NOT_SUPPORTED).asPromise();
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(final Context context,
                                                                     final String resourceId,
                                                                     final ActionRequest request) {
        return getException(NOT_SUPPORTED).asPromise();
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
