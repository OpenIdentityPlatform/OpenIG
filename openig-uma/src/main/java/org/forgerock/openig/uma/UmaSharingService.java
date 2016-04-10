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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.uma;

import static java.lang.String.format;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValueFunctions.listOf;
import static org.forgerock.json.JsonValueFunctions.pattern;
import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.json.resource.Resources.newCollection;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.util.promise.Promises.newExceptionPromise;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.Responses;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * An {@link UmaSharingService} provides core UMA features to OpenIG when acting as an UMA Resource Server.
 *
 * <p>It is linked to a single UMA Authorization Server and needs to be pre-registered as an OAuth 2.0 client on that
 * AS.
 *
 * <p>It is also the place where protected application knowledge is described: each item of the {@code resources}
 * array describe a resource set (that can be composed of multiple endpoints) that share the same set of scopes.
 *
 * <p>Each resource contains a {@code pattern} used to define which one of them to use when a {@link Share} is
 * {@linkplain #createShare(Context, String, String) created}. A resource also contains a list of {@code actions} that
 * defines the set of scopes to require when a requesting party request comes in.
 *
 * <pre>
 *     {@code {
 *         "name": "UmaService",
 *         "type": "UmaService",
 *         "config": {
 *           "protectionApiHandler": "HttpsClient",
 *           "authorizationServerUri": "https://openam.example.com:8443/openam",
 *           "clientId": "uma",
 *           "clientSecret": "welcome",
 *           "resources": [
 *             {
 *               "pattern": "/guillaume/.*",
 *               "actions" : [
 *                 {
 *                   "scopes"    : [ "http://api.example.com/operations#read" ],
 *                   "condition" : "${request.method == 'GET'}"
 *                 },
 *                 {
 *                   "scopes"    : [ "http://api.example.com/operations#delete" ],
 *                   "condition" : "${request.method == 'DELETE'}"
 *                 }
 *               ]
 *             }
 *           ]
 *         }
 *       }
 *     }
 * </pre>
 *
 * Along with the {@code UmaService}, a REST endpoint is deployed in OpenIG's API namespace:
 * {@literal /openig/api/system/objects/../objects/[name-of-the-uma-service-object]/share}.
 * The dotted segment depends on your deployment (like which RouterHandler hosts the route that
 * in turns contains this object).
 */
public class UmaSharingService {

    private final List<ShareTemplate> templates = new ArrayList<>();
    private final Map<String, Share> shares = new TreeMap<>();

    private final Handler protectionApiHandler;
    private final URI authorizationServer;
    private final URI introspectionEndpoint;
    private final URI ticketEndpoint;
    private final URI resourceSetEndpoint;
    private final String clientId;
    private final String clientSecret;

    /**
     * Constructs an UmaSharingService bound to the given {@code authorizationServer} and dedicated to protect resource
     * sets described by the given {@code templates}.
     *
     * @param protectionApiHandler
     *         used to call the resource set endpoint
     * @param templates
     *         list of resource descriptions
     * @param authorizationServer
     *         Bound UMA Authorization Server
     * @param clientId
     *         OAuth 2.0 Client identifier
     * @param clientSecret
     *         OAuth 2.0 Client secret
     * @throws URISyntaxException
     *         when the authorization server URI cannot be "normalized" (trailing '/' append if required)
     */
    public UmaSharingService(final Handler protectionApiHandler,
                             final List<ShareTemplate> templates,
                             final URI authorizationServer,
                             final String clientId,
                             final String clientSecret)
            throws URISyntaxException {
        this.protectionApiHandler = protectionApiHandler;
        this.templates.addAll(templates);
        this.authorizationServer = appendTrailingSlash(authorizationServer);
        // TODO Should find theses values looking at the .well-known/uma-configuration endpoint
        this.introspectionEndpoint = authorizationServer.resolve("oauth2/introspect");
        this.ticketEndpoint = authorizationServer.resolve("uma/permission_request");
        this.resourceSetEndpoint = authorizationServer.resolve("oauth2/resource_set");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Append a trailing {@literal /} if missing.
     *
     * @param uri
     *         URI to be "normalized"
     * @return a URI with a trailing {@literal /}
     * @throws URISyntaxException should never happen
     */
    private static URI appendTrailingSlash(final URI uri) throws URISyntaxException {
        if (!uri.getPath().endsWith("/")) {
            MutableUri mutable = new MutableUri(uri);
            mutable.setRawPath(uri.getRawPath().concat("/"));
            return mutable.asURI();
        }
        return uri;
    }

    /**
     * Creates a Share that will be used to protect the given {@code resourcePath}.
     *
     * @param context
     *         Context chain used to keep a relationship between requests (tracking)
     * @param resourcePath
     *         resource to be protected
     * @param pat
     *         Protection Api Token (PAT)
     * @return the created {@link Share} asynchronously
     * @see <a href="https://docs.kantarainitiative.org/uma/draft-oauth-resource-reg.html#rfc.section.2">Resource Set
     * Registration</a>
     */
    public Promise<Share, UmaException> createShare(final Context context,
                                                    final String resourcePath,
                                                    final String pat) {

        if (isShared(resourcePath)) {
            // We do not accept re-sharing or post-creation resource_set configuration
            return newExceptionPromise(new UmaException(format("Resource %s is already shared", resourcePath)));
        }

        // Need to find which ShareTemplate to use
        final ShareTemplate matching = findShareTemplate(resourcePath);

        if (matching == null) {
            return newExceptionPromise(new UmaException(format("Can't find a template for resource %s", resourcePath)));
        }

        return createResourceSet(context, matching, resourcePath, pat)
                .then(new Function<Response, Share, UmaException>() {
                    @Override
                    public Share apply(final Response response) throws UmaException {
                        if (response.getStatus() == Status.CREATED) {
                            try {
                                JsonValue value = json(response.getEntity().getJson());
                                Share share = new Share(matching, value, Pattern.compile(resourcePath), pat);
                                shares.put(share.getId(), share);
                                return share;
                            } catch (IOException e) {
                                throw new UmaException("Can't read the CREATE resource_set response", e);
                            }
                        }
                        throw new UmaException("Cannot register resource_set in AS");
                    }
                }, Responses.<Share, UmaException>noopExceptionFunction());
    }

    /**
     * Select, among the registered templates, the one that match best the resource path to be shared.
     *
     * @param resourcePath
     *         path of the resource to be shared
     * @return the best match, or {@code null} if no match have been found
     */
    private ShareTemplate findShareTemplate(final String resourcePath) {
        ShareTemplate matching = null;
        int longest = -1;
        for (ShareTemplate template : templates) {
            Matcher matcher = template.getPattern().matcher(resourcePath);
            if (matcher.matches() && (matcher.end() > longest)) {
                matching = template;
                longest = matcher.end();
            }
        }
        return matching;
    }

    private boolean isShared(final String path) {
        for (Share share : shares.values()) {
            if (path.equals(share.getPattern().toString())) {
                return true;
            }
        }
        return false;
    }

    private Promise<Response, NeverThrowsException> createResourceSet(final Context context,
                                                                      final ShareTemplate template,
                                                                      final String path,
                                                                      final String pat) {
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(resourceSetEndpoint);
        request.getHeaders().put("Authorization", format("Bearer %s", pat));
        request.getHeaders().put("Accept", "application/json");

        request.setEntity(resourceSet(path, template).asMap());

        return protectionApiHandler.handle(context, request);
    }

    private JsonValue resourceSet(final String name, final ShareTemplate template) {
        return json(object(field("name", uniqueName(name)),
                           field("scopes", template.getAllScopes())));
    }

    private String uniqueName(final String name) {
        // TODO this is a workaround until we have persistence on the OpenIG side
        return format("%s @ %d", name, System.currentTimeMillis());
    }

    /**
     * Find a {@link Share}.
     *
     * @param request
     *         the incoming requesting party request
     * @return a {@link Share} to be used to protect the resource access
     * @throws UmaException
     *         when no {@link Share} can handle the request.
     */
    public Share findShare(Request request) throws UmaException {

        // Need to find which Share to use
        // The logic here is that the longest matching segment denotes the best share
        //   request: /alice/allergies/pollen
        //   shares: [ /alice.*, /alice/allergies, /alice/allergies/pollen ]
        // expects the last share to be returned
        Share matching = null;
        String path = request.getUri().getPath();
        int longest = -1;
        for (Share share : shares.values()) {
            Matcher matcher = share.getPattern().matcher(path);
            if (matcher.matches() && matcher.end() > longest) {
                matching = share;
                longest = matcher.end();
            }
        }

        // Fail-fast if no shares matched
        if (matching == null) {
            throw new UmaException(format("Can't find any shared resource for %s", path));
        }

        return matching;
    }

    /**
     * Removes the previously created Share from the registered shares. In effect, the resources is no more
     * shared/protected
     *
     * @param shareId
     *         share identifier
     * @return the removed Share instance if found, {@code null} otherwise.
     */
    public Share removeShare(String shareId) {
        return shares.remove(shareId);
    }

    /**
     * Returns a copy of the list of currently managed shares.
     * @return a copy of the list of currently managed shares.
     */
    public Set<Share> listShares() {
        return new HashSet<>(shares.values());
    }

    /**
     * Returns the UMA authorization server base Uri.
     * @return the UMA authorization server base Uri.
     */
    public URI getAuthorizationServer() {
        return authorizationServer;
    }

    /**
     * Returns the UMA Permission Request endpoint Uri.
     * @return the UMA Permission Request endpoint Uri.
     */
    public URI getTicketEndpoint() {
        return ticketEndpoint;
    }

    /**
     * Returns the OAuth 2.0 Introspection endpoint Uri.
     * @return the OAuth 2.0 Introspection endpoint Uri.
     */
    public URI getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    /**
     * Returns the {@link Share} with the given {@code id}.
     * @param id Share identifier
     * @return the {@link Share} with the given {@code id} (or {@code null} if none was found).
     */
    public Share getShare(final String id) {
        return shares.get(id);
    }

    /**
     * Returns the client identifier used to identify this RS as an OAuth 2.0 client.
     * @return the client identifier used to identify this RS as an OAuth 2.0 client.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the client secret.
     * @return the client secret.
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Creates and initializes an UMA service in a heap environment.
     */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            Handler handler = config.get("protectionApiHandler").required().as(requiredHeapObject(heap, Handler.class));
            URI uri = config.get("authorizationServerUri").as(evaluated()).required().as(uri());
            String clientId = config.get("clientId").as(evaluated()).required().asString();
            String clientSecret = config.get("clientSecret").as(evaluated()).required().asString();
            try {
                UmaSharingService service = new UmaSharingService(handler,
                                                                  createResourceTemplates(),
                                                                  uri,
                                                                  clientId,
                                                                  clientSecret);
                // register admin endpoint
                Handler httpHandler = newHttpHandler(newCollection(new ShareCollectionProvider(service)));
                EndpointRegistry.Registration share = endpointRegistry().register("share", httpHandler);
                logger.info(format("UMA Share endpoint available at '%s'", share.getPath()));

                return service;
            } catch (URISyntaxException e) {
                throw new HeapException("Cannot build UmaSharingService", e);
            }
        }

        private List<ShareTemplate> createResourceTemplates() throws HeapException {
            return config.get("resources")
                         .required()
                         .as(listOf(new Function<JsonValue, ShareTemplate, HeapException>() {
                             @Override
                             public ShareTemplate apply(final JsonValue value) throws HeapException {
                                 return new ShareTemplate(value.get("pattern").required().as(pattern()),
                                                          actions(value.get("actions").expect(List.class)));
                             }
                         }));
        }

        private List<ShareTemplate.Action> actions(final JsonValue actions) {
            return actions.as(listOf(new Function<JsonValue, ShareTemplate.Action, JsonValueException>() {
                @Override
                public ShareTemplate.Action apply(final JsonValue value) {
                    return new ShareTemplate.Action(value.get("condition").required().as(expression(Boolean.class)),
                                                    value.get("scopes").as(evaluated()).asSet(String.class));
                }
            }));
        }
    }
}
