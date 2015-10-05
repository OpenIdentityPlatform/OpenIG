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

package org.forgerock.openig.openam;

import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.fieldIfNotNull;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.HTTP_CLIENT_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;

import java.net.URI;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.header.AcceptApiVersionHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

/**
 * This filter requests policy decisions from OpenAM which evaluates the
 * original URI based on the context and the policies configured, and according
 * to the decisions, allows or denies the current request.
 *
 * <pre>
 * {@code {
 *      "type": "PolicyEnforcementFilter",
 *      "config": {
 *          "openamUrl"         :    uriExpression,      [REQUIRED]
 *          "pepUsername"       :    expression,         [REQUIRED*]
 *          "pepPassword"       :    expression,         [REQUIRED*]
 *          "policiesHandler"   :    handler,            [OPTIONAL - default is using a new ClientHandler
 *                                                                   wrapping the default HttpClient.]
 *          "realm"             :    String,             [OPTIONAL]
 *          "application"       :    String,             [OPTIONAL]
 *          "ssoTokenSubject"   :    expression,         [OPTIONAL - must be specified if no jwtSubject ]
 *          "jwtSubject"        :    expression          [OPTIONAL - must be specified if no ssoTokenSubject ]
 *      }
 *  }
 * </pre>
 * <p>
 * (*) pepUsername and pepPassword are the credentials of the user who has
 * access to perform the operation, and these fields are required when using
 * heaplet. This heaplet adds an SsoTokenFilter to the policiesHandler's chain
 * and its role is to retrieve and set the SSO token header of this given user.
 * (REST API calls must present the session token, aka SSO Token, in the HTTP
 * header as proof of authentication)
 * <p>
 * Note: Claims are not supported right now.
 * <p>
 * Example of use:
 *
 * <pre>
 * {@code {
 *      "name": "PEPFilter",
 *      "type": "PolicyEnforcementFilter",
 *      "config": {
 *          "openamUrl": "http://example.com:8090/openam/",
 *          "pepUsername": "bjensen",
 *          "pepPassword": "${exchange.attributes.userpass}",
 *          "application": "myApplication",
 *          "ssoTokenSubject": ${exchange.attributes.SSOCurrentUser}
 *      }
 *  }
 * </pre>
 */
public class PolicyEnforcementFilter extends GenericHeapObject implements Filter {

    private static final String BASE_ENDPOINT = "json/";
    private static final String POLICY_ENDPOINT = "/policies";
    private static final String EVALUATE_ACTION = "evaluate";
    private static final String SUBJECT_ERROR = "The attribute 'ssoTokenSubject' or 'jwtSubject' must be specified";

    private final Handler policiesHandler;
    private final URI openamUrl;
    private final String realm;
    private String application;
    private Expression<String> ssoTokenSubject;
    private Expression<String> jwtSubject;

    /**
     * Creates a new OpenAM enforcement filter.
     *
     * @param openamUrl
     *            The location of the selected OpenAM instance.
     * @param realm
     *            The targeted realm.
     * @param policiesHandler
     *            The handler used to get perform policies requests.
     */
    public PolicyEnforcementFilter(final URI openamUrl,
                                   final String realm,
                                   final Handler policiesHandler) {
        this.openamUrl = openamUrl;
        this.realm = realm;
        this.policiesHandler = chainOf(policiesHandler,
                                       new ApiVersionProtocolHeaderFilter());
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        return askForPolicyDecision(context, request)
                    .then(evaluatePolicyDecision(request))
                    .thenAsync(allowOrDenyAccessToResource(context, request, next),
                               returnInternalServerError);
    }

    /**
     * Sets the application where the policies are defined. If none, OpenAM will
     * use the iPlanetAMWebAgentService.
     *
     * @param application
     *            The application where the policies are defined. If none,
     *            OpenAM will use the iPlanetAMWebAgentService.
     */
    public void setApplication(final String application) {
        this.application = application;
    }

    /**
     * Sets the SSO token for the subject.
     *
     * @param ssoTokenSubject
     *            The SSO Token for the subject.
     */
    public void setSsoTokenSubject(final Expression<String> ssoTokenSubject) {
        this.ssoTokenSubject = ssoTokenSubject;
    }

    /**
     * Sets the JWT string for the subject.
     *
     * @param jwtSubject
     *            The JWT string for the subject.
     */
    public void setJwtSubject(final Expression<String> jwtSubject) {
        this.jwtSubject = jwtSubject;
    }

    private AsyncFunction<ResourceException, Response, NeverThrowsException> returnInternalServerError =
            new AsyncFunction<ResourceException, Response, NeverThrowsException>() {

                @Override
                public Promise<Response, NeverThrowsException> apply(ResourceException exception) {
                    logger.debug(exception);
                    final Response errorResponse = new Response(INTERNAL_SERVER_ERROR);
                    errorResponse.setCause(exception);
                    return newResponsePromise(errorResponse);
                }
            };

    private AsyncFunction<Boolean, Response, NeverThrowsException> allowOrDenyAccessToResource(
            final Context context, final Request request, final Handler next) {
        return new AsyncFunction<Boolean, Response, NeverThrowsException>() {

            @Override
            public Promise<Response, NeverThrowsException> apply(final Boolean authorized) {
                if (authorized) {
                    return next.handle(context, request);
                }
                return newResponsePromise(new Response(UNAUTHORIZED));
            }
        };
    }

    private Promise<JsonValue, ResourceException> askForPolicyDecision(final Context context,
                                                                       final Request request) {
        final Exchange exchange = context.asContext(Exchange.class);
        final RequestHandler requestHandler = CrestHttp.newRequestHandler(policiesHandler,
                                                                          openamUrl.resolve(BASE_ENDPOINT + realm));
        final ActionRequest actionRequest = Requests.newActionRequest(ResourcePath.valueOf(POLICY_ENDPOINT),
                                                                      EVALUATE_ACTION);

        final Map<?, ?> subject =
                json(object(
                          fieldIfNotNull("ssoToken", ssoTokenSubject != null ? ssoTokenSubject.eval(exchange) : null),
                          fieldIfNotNull("jwt", jwtSubject != null ? jwtSubject.eval(exchange) : null))).asMap();

        if (subject.isEmpty()) {
            logger.error(SUBJECT_ERROR);
            return new NotSupportedException().asPromise();
        }

        final JsonValue resources = json(object(
                                            field("resources", array(request.getUri().toASCIIString())),
                                            field("subject", subject),
                                            fieldIfNotNull("application", application)));
        actionRequest.setContent(resources);
        actionRequest.setResourceVersion(version(2, 0));
        return requestHandler.handleAction(context, actionRequest)
                             .then(EXTRACT_POLICY_DECISION_AS_JSON);
    }

    private static final Function<ActionResponse, JsonValue, ResourceException> EXTRACT_POLICY_DECISION_AS_JSON =
            new Function<ActionResponse, JsonValue, ResourceException>() {

                @Override
                public JsonValue apply(final ActionResponse policyResponse) {
                    // The policy response is an array
                    return policyResponse.getJsonContent().get(0);
                }
            };

    private static Function<JsonValue, Boolean, ResourceException> evaluatePolicyDecision(final Request request) {
        return new Function<JsonValue, Boolean, ResourceException>() {

            @Override
            public Boolean apply(final JsonValue policyDecision) {
                final MutableUri original = request.getUri();
                if (policyDecision.get("resource").asString().equals(original.toASCIIString())) {
                    final String method = request.getMethod();
                    final Map<String, Object> actions = policyDecision.get("actions").asMap();
                    if (actions.containsKey(method)) {
                        return (boolean) actions.get(method);
                    }
                }
                return false;
            }
        };
    }

    /** Creates and initializes a policy enforcement filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {
        @Override
        public Object create() throws HeapException {

            final URI openamUrl = config.get("openamUrl").required().asURI();
            final Expression<String> pepUsername = asExpression(config.get("pepUsername").required(), String.class);
            final Expression<String> pepPassword = asExpression(config.get("pepPassword").required(), String.class);
            final String realm = config.get("realm").defaultTo("/").asString();
            final Handler policiesHandler;
            if (config.isDefined("policiesHandler")) {
                policiesHandler = heap.resolve(config.get("policiesHandler"), Handler.class);
            } else {
                policiesHandler = new ClientHandler(heap.get(HTTP_CLIENT_HEAP_KEY, HttpClient.class));
            }
            final SsoTokenFilter ssoTokenFilter = new SsoTokenFilter(policiesHandler,
                                                                     openamUrl,
                                                                     realm,
                                                                     pepUsername,
                                                                     pepPassword);

            final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(openamUrl,
                                                                               realm,
                                                                               chainOf(policiesHandler,
                                                                                       ssoTokenFilter));
            filter.setApplication(config.get("application").asString());
            filter.setSsoTokenSubject(asExpression(config.get("ssoTokenSubject"), String.class));
            filter.setJwtSubject(asExpression(config.get("jwtSubject"), String.class));
            if (config.get("ssoTokenSubject").isNull() && config.get("jwtSubject").isNull()) {
                throw new HeapException(SUBJECT_ERROR);
            }

            return filter;
        }
    }

    private class ApiVersionProtocolHeaderFilter implements Filter {

        @Override
        public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
            // The protocol versions supported in OPENAM-13 is 1.0 and
            // CREST adapter forces to 2.0, throwing a 'Unsupported major
            // version: 2.0' exception if not set. CREST operation(action) is
            // compatible between protocol v1 and v2
            request.getHeaders().put(AcceptApiVersionHeader.NAME, "protocol=1.0, resource=2.0");
            return next.handle(context, request);
        }
    }
}
