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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.fieldIfNotNull;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.asExpression;
import static org.forgerock.openig.util.StringUtil.trailingSlash;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.time.Duration.duration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.header.AcceptApiVersionHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.heap.GenericHeapObject;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;

/**
 * This filter requests policy decisions from OpenAM which evaluates the
 * original URI based on the context and the policies configured, and according
 * to the decisions, allows or denies the current request.
 * <p>
 * Policy decisions are cached for each filter and eviction is based on the
 * "time-to-live" given in the policy decision returned by AM, if this one
 * exceed the duration expressed in the cacheMaxExpiration, then the value of
 * cacheMaxExpiration is used to cache the policy.
 *
 * <pre>
 * {@code {
 *      "type": "PolicyEnforcementFilter",
 *      "config": {
 *          "openamUrl"              :    uriExpression,      [REQUIRED]
 *          "pepUsername"            :    expression,         [REQUIRED*]
 *          "pepPassword"            :    expression,         [REQUIRED*]
 *          "policiesHandler"        :    handler,            [OPTIONAL - by default it uses the 'ClientHandler'
 *                                                                        provided in heap.]
 *          "realm"                  :    String,             [OPTIONAL]
 *          "ssoTokenHeader"         :    String,             [OPTIONAL]
 *          "application"            :    String,             [OPTIONAL]
 *          "ssoTokenSubject"        :    expression,         [OPTIONAL - must be specified if no jwtSubject ]
 *          "jwtSubject"             :    expression,         [OPTIONAL - must be specified if no ssoTokenSubject ]
 *          "cacheMaxExpiration"     :    duration            [OPTIONAL - default to 1 minute ]
 *      }
 *  }
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
 *          "pepPassword": "${attributes.userpass}",
 *          "application": "myApplication",
 *          "ssoTokenSubject": ${attributes.SSOCurrentUser}
 *      }
 *  }
 *  }
 * </pre>
 */
public class PolicyEnforcementFilter extends GenericHeapObject implements Filter {

    private static final String ONE_MINUTE = "1 minute";
    private static final String POLICY_ENDPOINT = "/policies";
    private static final String EVALUATE_ACTION = "evaluate";
    private static final String SUBJECT_ERROR = "The attribute 'ssoTokenSubject' or 'jwtSubject' must be specified";

    private ThreadSafeCache<String, Promise<JsonValue, ResourceException>> policyDecisionCache;
    private final URI baseUri;
    private final Duration cacheMaxExpiration;
    private final Handler policiesHandler;
    private String application;
    private Expression<String> ssoTokenSubject;
    private Expression<String> jwtSubject;

    @VisibleForTesting
    PolicyEnforcementFilter(final URI baseUri, final Handler policiesHandler) {
        this(baseUri, policiesHandler, duration(ONE_MINUTE));
    }

    /**
     * Creates a new OpenAM enforcement filter.
     *
     * @param baseUri
     *            The location of the selected OpenAM instance, including the
     *            realm, to the json base endpoint, not {@code null}.
     * @param policiesHandler
     *            The handler used to get perform policies requests, not {@code null}.
     * @param cacheMaxExpiration
     *            The max duration to set the cache.
     */
    public PolicyEnforcementFilter(final URI baseUri,
                                   final Handler policiesHandler,
                                   final Duration cacheMaxExpiration) {
        this.baseUri = checkNotNull(baseUri);
        this.cacheMaxExpiration = cacheMaxExpiration;
        this.policiesHandler = chainOf(checkNotNull(policiesHandler),
                                       new ApiVersionProtocolHeaderFilter());
    }

    /**
     * Sets the cache for the policy decisions.
     *
     * @param cache
     *            The cache for policy decisions to set.
     */
    public void setCache(final ThreadSafeCache<String, Promise<JsonValue, ResourceException>> cache) {
        this.policyDecisionCache = cache;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        return askForPolicyDecision(context, request)
                    .then(evaluatePolicyDecision(request))
                    .thenAsync(allowOrDenyAccessToResource(context, request, next),
                               returnUnauthorizedResponse);
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

    private AsyncFunction<ResourceException, Response, NeverThrowsException> returnUnauthorizedResponse =
            new AsyncFunction<ResourceException, Response, NeverThrowsException>() {

                @Override
                public Promise<Response, NeverThrowsException> apply(ResourceException exception) {
                    logger.debug("Cannot get the policy evaluation");
                    logger.debug(exception);
                    final Response response = new Response(UNAUTHORIZED);
                    response.setCause(exception);
                    return newResponsePromise(response);
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
        final RequestHandler requestHandler = CrestHttp.newRequestHandler(policiesHandler, baseUri);
        final ActionRequest actionRequest = Requests.newActionRequest(ResourcePath.valueOf(POLICY_ENDPOINT),
                                                                      EVALUATE_ACTION);

        Bindings bindings = bindings(context, request);
        final Map<?, ?> subject =
                json(object(
                          fieldIfNotNull("ssoToken", ssoTokenSubject != null ? ssoTokenSubject.eval(bindings) : null),
                          fieldIfNotNull("jwt", jwtSubject != null ? jwtSubject.eval(bindings) : null))).asMap();

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

        final String key = createKeyCache((String) subject.get("ssoToken"),
                                          (String) subject.get("jwt"),
                                          request.getUri().toASCIIString());

        try {
            return policyDecisionCache.getValue(key,
                                                getPolicyDecisionCallable(context, requestHandler, actionRequest),
                                                extractDurationFromTtl());
        } catch (InterruptedException | ExecutionException e) {
            return new InternalServerErrorException(e).asPromise();
        }
    }

    private AsyncFunction<Promise<JsonValue, ResourceException>, Duration, Exception>
    extractDurationFromTtl() {
        return new AsyncFunction<Promise<JsonValue, ResourceException>, Duration, Exception>() {

            @Override
            public Promise<Duration, Exception> apply(Promise<JsonValue, ResourceException> value) throws Exception {
                return value.thenAsync(new AsyncFunction<JsonValue, Duration, Exception>() {

                    @Override
                    public Promise<? extends Duration, ? extends ResourceException> apply(JsonValue value)
                            throws Exception {
                        final Duration timeout = new Duration(value.get("ttl").asLong(), MILLISECONDS);
                        if (timeout.to(MILLISECONDS) > cacheMaxExpiration.to(MILLISECONDS)) {
                            return newResultPromise(cacheMaxExpiration);
                        }
                        return newResultPromise(timeout);
                    }
                }, new AsyncFunction<ResourceException, Duration, Exception>() {

                    @Override
                    public Promise<? extends Duration, ? extends Exception> apply(ResourceException e)
                            throws Exception {
                        return newResultPromise(new Duration(1L, SECONDS));
                    }
                });
            }
        };
    }

    private static Callable<Promise<JsonValue, ResourceException>> getPolicyDecisionCallable(
                                                                                  final Context context,
                                                                                  final RequestHandler requestHandler,
                                                                                  final ActionRequest actionRequest) {
        return new Callable<Promise<JsonValue, ResourceException>>() {

            @Override
            public Promise<JsonValue, ResourceException> call() throws Exception {
                return requestHandler.handleAction(context, actionRequest)
                                     .then(EXTRACT_POLICY_DECISION_AS_JSON);
            }
        };
    }

    @VisibleForTesting
    static String createKeyCache(final String ssoToken, final String jwt, final String requestedUri) {
        return new StringBuilder(requestedUri).append(ifSpecified(ssoToken)).append(ifSpecified(jwt)).toString();
    }

    private static String ifSpecified(final String value) {
        if (value != null && !value.isEmpty()) {
            return "@" + value;
        }
        return "";
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

        private ScheduledExecutorService executor;
        private ThreadSafeCache<String, Promise<JsonValue, ResourceException>> cache;

        @Override
        public Object create() throws HeapException {

            final String openamUrl = trailingSlash(config.get("openamUrl").required().asString());
            final Expression<String> pepUsername = asExpression(config.get("pepUsername").required(), String.class);
            final Expression<String> pepPassword = asExpression(config.get("pepPassword").required(), String.class);
            final String realm = config.get("realm").defaultTo("/").asString();
            final Handler policiesHandler = heap.resolve(config.get("policiesHandler")
                                                               .defaultTo(CLIENT_HANDLER_HEAP_KEY),
                                                         Handler.class);
            final String ssoTokenHeader = config.get("ssoTokenHeader").asString();

            final Duration cacheMaxExpiration = duration(config.get("cacheMaxExpiration").defaultTo(ONE_MINUTE)
                                                                                         .asString());
            if (cacheMaxExpiration.isZero() || cacheMaxExpiration.isUnlimited()) {
                throw new HeapException("The max expiration value cannot be set to 0 or to 'unlimited'");
            }

            try {
                final SsoTokenFilter ssoTokenFilter = new SsoTokenFilter(policiesHandler,
                                                                         new URI(openamUrl),
                                                                         realm,
                                                                         ssoTokenHeader,
                                                                         pepUsername,
                                                                         pepPassword);

                final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(normalizeToJsonEndpoint(openamUrl,
                                                                                                           realm),
                                                                                   chainOf(policiesHandler,
                                                                                           ssoTokenFilter),
                                                                                   cacheMaxExpiration);

                filter.setApplication(config.get("application").asString());
                filter.setSsoTokenSubject(asExpression(config.get("ssoTokenSubject"), String.class));
                filter.setJwtSubject(asExpression(config.get("jwtSubject"), String.class));
                if (config.get("ssoTokenSubject").isNull() && config.get("jwtSubject").isNull()) {
                    throw new HeapException(SUBJECT_ERROR);
                }

                // Sets the cache
                executor = Executors.newSingleThreadScheduledExecutor();
                cache = new ThreadSafeCache<>(executor);
                filter.setCache(cache);

                return filter;
            } catch (URISyntaxException e) {
                throw new HeapException(e);
            }
        }

        @VisibleForTesting
        static URI normalizeToJsonEndpoint(final String openamUri, final String realm) throws URISyntaxException {
            final StringBuilder builder = new StringBuilder(openamUri);
            builder.append("json");
            if (realm == null || realm.trim().isEmpty()) {
                builder.append("/");
            } else {
                if (!realm.startsWith("/")) {
                    builder.append("/");
                }
                builder.append(trailingSlash(realm.trim()));
            }
            return new URI(builder.toString());
        }

        @Override
        public void destroy() {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (cache != null) {
                cache.clear();
            }
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
