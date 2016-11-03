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

package org.forgerock.openig.openam;

import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.http.util.Uris.urlEncodePathElement;
import static org.forgerock.http.util.Uris.withQuery;
import static org.forgerock.json.JsonValueFunctions.uri;
import static org.forgerock.json.resource.Requests.newActionRequest;
import static org.forgerock.json.resource.http.CrestHttp.newRequestHandler;
import static org.forgerock.json.resource.http.HttpUtils.PROTOCOL_VERSION_1;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.openig.util.JsonValues.slashEnded;
import static org.forgerock.util.Reject.checkNotNull;

import java.net.URI;
import java.util.List;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.header.LocationHeader;
import org.forgerock.http.protocol.Cookie;
import org.forgerock.http.protocol.Form;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.routing.Version;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter verifies the presence of a SSOToken in the given cookie name.
 * If the request cookie header contains a SSOToken, its validity is verified
 * before the request is forwarded to the next handler.
 * <p>
 * If the SSOToken is not valid or if cookie header is not present
 * or empty, then the user-agent is redirected to OpenAM login page.
 * Once log in has been successful, the request is forwarded.
 *
 * <pre>
 * {@code {
 *    "type": "SingleSignOnFilter",
 *    "config": {
 *        "openamUrl"              :    uriExpression      [REQUIRED]
 *        "cookieName"             :    String             [OPTIONAL - by default is 'iPlanetDirectoryPro']
 *        "realm"                  :    String             [OPTIONAL - default is '/']
 *        "amHandler"              :    handler            [OPTIONAL - by default it uses the
 *                                                                     'ForgeRockClientHandler' provided in heap.]
 *    }
 *  }
 *  }
 * </pre>
 **/
public class SingleSignOnFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(SingleSignOnFilter.class);

    private static final String DEFAULT_COOKIE_NAME = "iPlanetDirectoryPro";
    private static final Version RESOURCE_VERSION_1_1 = version(1, 1);
    private static final String SESSIONS_ENDPOINT = "/json/sessions/";
    private static final String VALIDATE_ACTION = "validate";

    private final URI openamUri;
    private final String cookieName;
    private final String realm;
    private final RequestHandler requestHandler;

    SingleSignOnFilter(final URI openamUri,
                       final String cookieName,
                       final String realm,
                       final RequestHandler requestHandler) {
        this.openamUri = checkNotNull(openamUri, "The openamUrl must be specified");
        this.cookieName = checkNotNull(cookieName, "The cookie name must be specified");
        this.realm = checkNotNull(realm, "The realm must be specified");
        this.requestHandler = checkNotNull(requestHandler, "The requestHandler must be specified");
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        final String redirectUri = buildRedirectUri(context);

        final List<Cookie> authCookies = request.getCookies().get(cookieName);
        if (authCookies != null && !authCookies.isEmpty()) {
            if (authCookies.size() > 1) {
                logger.warn("Multiple {} cookie headers detected", cookieName);
            }
            final String ssoToken = authCookies.get(0).getValue();
            final ActionRequest validateSessionRequest = newActionRequest(
                    ResourcePath.valueOf(SESSIONS_ENDPOINT + urlEncodePathElement(ssoToken)), VALIDATE_ACTION);
            validateSessionRequest.setResourceVersion(RESOURCE_VERSION_1_1);

            return requestHandler.handleAction(context, validateSessionRequest)
                                 .thenAsync(checkResponse(next, context, request, redirectUri, ssoToken),
                                            errorResponse);
        }
        return httpRedirect(redirectUri);
    }

    private String buildRedirectUri(final Context context) {
        final UriRouterContext routerContext = context.asContext(UriRouterContext.class);
        final URI originalUri = routerContext.getOriginalUri();
        final Form query = new Form();
        query.add("goto", originalUri.toASCIIString());
        query.add("realm", realm);
        return withQuery(openamUri, query).toASCIIString();
    }

    private static Promise<Response, NeverThrowsException> httpRedirect(final String uri) {
        final Response response = new Response(Status.FOUND);
        response.getHeaders().add(LocationHeader.NAME, uri);
        return newResponsePromise(response);
    }

    private AsyncFunction<ResourceException, Response, NeverThrowsException> errorResponse =
            new AsyncFunction<ResourceException, Response, NeverThrowsException>() {

                @Override
                public Promise<Response, NeverThrowsException> apply(final ResourceException exception) {
                    logger.error("An error occurred when validating the token", exception);
                    return newResponsePromise(newInternalServerError(exception));
                }
            };

    final static AsyncFunction<ActionResponse, Response, NeverThrowsException> checkResponse(final Handler next,
                                                                                             final Context context,
                                                                                             final Request request,
                                                                                             final String redirectUri,
                                                                                             final String ssoToken) {
        return new AsyncFunction<ActionResponse, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final ActionResponse actionResponse) {

                final JsonValue result = actionResponse.getJsonContent();
                final JsonValue valid = result.get("valid");
                if (valid.isNull()) {
                    logger.debug("Unable to get a result from the token validation");
                    return newResponsePromise(newInternalServerError());
                }
                if (valid.asBoolean()) {
                    return next.handle(new SsoTokenContext(context, result, ssoToken), request);
                }
                return httpRedirect(redirectUri);
            }
        };
    }

    /** Creates and initialises an authentication filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        @Override
        public Object create() throws HeapException {
            final URI openamUri = config.get("openamUrl")
                                        .as(evaluatedWithHeapProperties())
                                        .required()
                                        .as(slashEnded())
                                        .as(uri());

            final String cookieName = config.get("cookieName")
                                            .as(evaluatedWithHeapProperties())
                                            .defaultTo(DEFAULT_COOKIE_NAME)
                                            .asString();

            final String realm = config.get("realm")
                                       .as(evaluatedWithHeapProperties())
                                       .defaultTo("/")
                                       .asString();

            final Handler amHandler = config.get("amHandler")
                                            .as(evaluatedWithHeapProperties())
                                            .defaultTo(FORGEROCK_CLIENT_HANDLER_HEAP_KEY)
                                            .as(requiredHeapObject(heap, Handler.class));

            return new SingleSignOnFilter(openamUri,
                                          cookieName,
                                          realm,
                                          newRequestHandler(chainOf(amHandler,
                                                                    new ApiVersionProtocolHeaderFilter(
                                                                              PROTOCOL_VERSION_1)),
                                                              openamUri)
            );
        }
    }
}
