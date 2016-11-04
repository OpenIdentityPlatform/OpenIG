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

package org.forgerock.openig.openam;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.forgerock.http.handler.Handlers.chainOf;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Responses.newInternalServerError;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.fieldIfNotNull;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.json.resource.http.CrestHttp.newRequestHandler;
import static org.forgerock.json.resource.http.HttpUtils.PROTOCOL_VERSION_1;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TIME_SERVICE_HEAP_KEY;
import static org.forgerock.openig.util.JsonValues.evaluated;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;
import static org.forgerock.openig.util.StringUtil.trailingSlash;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.util.time.Duration.duration;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Responses;
import org.forgerock.json.JsonException;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.FilterChain;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourcePath;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.util.JsonValues;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;
import org.forgerock.util.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter requests policy decisions from OpenAM which evaluates the
 * original URI based on the context and the policies configured, and according
 * to the decisions, allows or denies the current request.
 * <p>
 * If the decision denies the request, a 403 FORBIDDEN is returned.
 * If an error occurred during the process, a 500 INTERNAL SERVER ERROR is returned.
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
 *          "pepRealm"               :    String,             [OPTIONAL*- default value is the one used for "realm"
 *                                                                        attribute]
 *          "amHandler"              :    handler,            [OPTIONAL - by default it uses the
 *                                                                        'ForgeRockClientHandler' provided in heap.]
 *          "realm"                  :    String,             [OPTIONAL - default is '/']
 *          "ssoTokenHeader"         :    String,             [OPTIONAL]
 *          "application"            :    String,             [OPTIONAL]
 *          "ssoTokenSubject"        :    expression,         [OPTIONAL - must be specified if no jwtSubject or
 *                                                                        claimsSubject ]
 *          "jwtSubject"             :    expression,         [OPTIONAL - must be specified if no ssoTokenSubject or
 *                                                                        claimsSubject ]
 *          "claimsSubject"          :    map/expression,     [OPTIONAL - must be specified if no jwtSubject or
 *                                                                        ssoTokenSubject - instance of
 *                                                                        Map<String, Object> JWT claims ]
 *          "environment"            :    map/expression,     [OPTIONAL - instance of Map<String, List<Object>>]
 *          "executor"               :    executor,           [OPTIONAL - by default uses 'ScheduledThreadPool'
 *                                                                        heap object]
 *          "failureHandler          :    handler,            [OPTIONAL - default to 403]
 *          "cache"                  :    object,             [OPTIONAL - cache configuration. Default is no caching.]
 *              "enabled"            :    boolean,            [OPTIONAL - default to false. Enable or not the caching of
 *                                                                        the policy decisions.]
 *              "defaultTimeout"     :    duration,           [OPTIONAL - default to 1 minute. If no valid ttl value is
 *                                                                        provided by the policy decision, we'll cache
 *                                                                        it during that duration.]
 *              "maxTimeout"         :    duration,           [OPTIONAL - If a value is provided by the policy decision
 *                                                                        but is greater that this value then we'll use
 *                                                                        that value. ("zero" and "unlimited" are not
 *                                                                        acceptable values).]
 *      }
 *  }
 *  }
 * </pre>
 * <p>
 * (*) "pepUsername" and "pepPassword" are the credentials, and "pepRealm" is the
 * realm of the user who has access to perform the operation.
 * <p>
 * This heaplet adds an HeadlessAuthenticationFilter to the amHandler's chain and its
 * role is to retrieve and set the SSO token header of this given user (REST API
 * calls must present the session token, aka SSO Token, in an HTTP header as
 * proof of authentication).
 * <p>
 * The "attributes" and "advices" from the policy decision are saved in a {@link PolicyDecisionContext}.
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
 *          "pepPassword": "${system['pep.password']}",
 *          "application": "myApplication",
 *          "ssoTokenSubject": "${attributes.SSOCurrentUser}",
 *          "claimsSubject": "${attributes.claimsSubject}",
 *          "environment": {
 *              "DAY_OF_WEEK": [
 *                  "Saturday"
 *              ]
 *          }
 *      }
 *  }
 *  }
 * </pre>
 *
 * @see <a href="http://openam.forgerock.org/doc/bootstrap/dev-guide/index.html#rest-api-authz-policy-decisions">
 *      Requesting Policy Decisions in OpenAM</a>
 */
public class PolicyEnforcementFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEnforcementFilter.class);

    private static final String POLICY_ENDPOINT = "/policies";
    private static final String EVALUATE_ACTION = "evaluate";
    private static final String SUBJECT_ERROR =
            "The attribute 'ssoTokenSubject' or 'jwtSubject' or 'claimsSubject' must be specified";

    private final RequestHandler requestHandler;
    private String application;
    private Expression<String> ssoTokenSubject;
    private Expression<String> jwtSubject;
    private Function<Bindings, Map<String, Object>, ExpressionException> claimsSubject;
    private Function<Bindings, Map<String, List<Object>>, ExpressionException> environment;
    private Handler failureHandler;

    @VisibleForTesting
    PolicyEnforcementFilter(final RequestHandler requestHandler) {
        this(requestHandler, Handlers.FORBIDDEN);
    }

    /**
     * Creates a new OpenAM enforcement filter.
     *
     * @param requestHandler
     *            the CREST handler to use for asking the policy decisions.
     * @param failureHandler
     *            The handler which will be invoked when policy denies access.
     */
    public PolicyEnforcementFilter(final RequestHandler requestHandler,
                                   final Handler failureHandler) {
        this.requestHandler = checkNotNull(requestHandler);
        this.failureHandler = checkNotNull(failureHandler);
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(final Context context,
                                                          final Request request,
                                                          final Handler next) {

        return askForPolicyDecision(context, request)
                .thenAsync(enforceDecision(context, request, next), Responses.<ResourceException>internalServerError());
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
     * Sets a function that returns a map of JWT claims to their values, for the
     * subject.
     *
     * @param claimsSubject
     *            A function that returns a map of JWT claims for the subject.
     */
    public void setClaimsSubject(final Function<Bindings, Map<String, Object>, ExpressionException> claimsSubject) {
        this.claimsSubject = claimsSubject;
    }

    /**
     * The environment passed from the client making the authorization request
     * as a sets a map of keys to lists of values.
     *
     * @param environment
     *            A function that returns a map of keys to lists of values.
     */
    public void setEnvironment(final Function<Bindings, Map<String, List<Object>>, ExpressionException> environment) {
        this.environment = environment;
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

    private Promise<JsonValue, ResourceException> askForPolicyDecision(final Context context,
                                                                       final Request request) {
        final ActionRequest actionRequest = Requests.newActionRequest(ResourcePath.valueOf(POLICY_ENDPOINT),
                                                                      EVALUATE_ACTION);

        JsonValue resources;
        try {
            resources = buildResources(context, request);
        } catch (NotSupportedException | ExpressionException ex) {
            logger.error("Unable to build the resources content", ex);
            return new InternalServerErrorException(ex).asPromise();
        }
        actionRequest.setContent(resources);
        actionRequest.setResourceVersion(version(2, 0));

        return requestHandler.handleAction(context, actionRequest)
                             .then(EXTRACT_POLICY_DECISION_AS_JSON);
    }

    @SuppressWarnings("raw")
    private AsyncFunction<JsonValue, Response, NeverThrowsException> enforceDecision(final Context context,
                                                                                     final Request request,
                                                                                     final Handler next) {
        return new AsyncFunction<JsonValue, Response, NeverThrowsException>() {
            @Override
            public Promise<Response, NeverThrowsException> apply(final JsonValue policyDecision) {
                String resource = policyDecision.get("resource").asString();
                String original = request.getUri().toASCIIString();
                if (resource.equals(original)) {
                    PolicyDecisionContext decisionContext =
                            new PolicyDecisionContext(context,
                                                      policyDecision.get("attributes").defaultTo(object()),
                                                      policyDecision.get("advices").defaultTo(object()));

                    boolean actionAllowed = policyDecision.get("actions")
                                                          .get(request.getMethod())
                                                          .defaultTo(false)
                                                          .asBoolean();

                    if (actionAllowed) {
                        return next.handle(decisionContext, request);
                    }
                    return failureHandler.handle(decisionContext, request);
                }

                // Should never happen
                logger.error("Returned resource ('{}' does not match current request URI (''))", resource, original);
                return newResponsePromise(newInternalServerError());
            }
        };
    }

    @VisibleForTesting
    JsonValue buildResources(final Context context, final Request request) throws ExpressionException,
                                                                                  NotSupportedException {
        final Bindings bindings = bindings(context, request);
        final Map<String, Object> subject =
                object(fieldIfNotNull("ssoToken", ssoTokenSubject != null ? ssoTokenSubject.eval(bindings) : null),
                       fieldIfNotNull("jwt", jwtSubject != null ? jwtSubject.eval(bindings) : null),
                       fieldIfNotNull("claims", claimsSubject != null ? claimsSubject.apply(bindings) : null));

        if (subject.isEmpty()) {
            logger.error(SUBJECT_ERROR);
            throw new NotSupportedException(SUBJECT_ERROR);
        }

        return json(object(field("resources", array(request.getUri().toASCIIString())),
                           field("subject", subject),
                           fieldIfNotNull("application", application),
                           fieldIfNotNull("environment", environment != null ? environment.apply(bindings) : null)));
    }

    private static final Function<ActionResponse, JsonValue, ResourceException> EXTRACT_POLICY_DECISION_AS_JSON =
            new Function<ActionResponse, JsonValue, ResourceException>() {

                @Override
                public JsonValue apply(final ActionResponse policyResponse) {
                    // The policy response is an array
                    return policyResponse.getJsonContent().get(0);
                }
            };

    static class CachePolicyDecisionFilter extends NotSupportedFilter implements Closeable {

        public static final Function<ActionResponse, ActionResponse, ResourceException> COPY_ACTION_RESPONSE =
                new Function<ActionResponse, ActionResponse, ResourceException>() {
                    @Override
                    public ActionResponse apply(ActionResponse actionResponse)
                            throws ResourceException {
                        return newActionResponse(actionResponse.getJsonContent().copy());
                    }
                };

        private final PerItemEvictionStrategyCache<String, Promise<ActionResponse, ResourceException>> cache;

        public CachePolicyDecisionFilter(ScheduledExecutorService executor,
                                         TimeService timeService,
                                         Duration defaultTimeout,
                                         Duration maxTimeout) {
            this.cache = new PerItemEvictionStrategyCache<>(executor,
                                                            extractDurationFromTtl(defaultTimeout, timeService));
            if (maxTimeout != null) {
                cache.setMaxTimeout(maxTimeout);
            }
        }

        @Override
        public Promise<ActionResponse, ResourceException> filterAction(final Context context,
                                                                       final ActionRequest request,
                                                                       final RequestHandler next) {
            // We expect a valid request to build the key. But if we are not able to build the key because the request
            // is invalid, then just forward to OpenAM, that will certainly answer a detailed message explaining why
            // the request is invalid.
            JsonValue requestContent = request.getContent();
            final JsonValue subject = requestContent.get("subject");
            final JsonValue resources = requestContent.get("resources");
            final JsonValue environment = requestContent.get("environment");
            if (subject.isNotNull() && resources.isNotNull()) {
                final String key = createKeyCache(resources.get(0).asString(),
                                                  subject.get("ssoToken").asString(),
                                                  subject.get("jwt").asString(),
                                                  subject.get("claims").asMap() != null
                                                    ? subject.get("claims").asMap().hashCode()
                                                    : 0,
                                                  environment.asMap() != null
                                                    ? environment.asMap().hashCode()
                                                    : 0);
                Callable<Promise<ActionResponse, ResourceException>> callable =
                        new Callable<Promise<ActionResponse, ResourceException>>() {
                            @Override
                            public Promise<ActionResponse, ResourceException> call() throws Exception {
                                return next.handleAction(context, request);
                            }
                        };
                try {
                    return cache.getValue(key, callable)
                                .then(copyActionResponse());
                } catch (InterruptedException | ExecutionException e) {
                    return new InternalServerErrorException(e).asPromise();
                }
            }
            return next.handleAction(context, request);
        }

        private static Function<ActionResponse, ActionResponse, ResourceException> copyActionResponse() {
            return COPY_ACTION_RESPONSE;
        }

        @VisibleForTesting
        static String createKeyCache(final String requestedUri,
                                     final String ssoToken,
                                     final String jwt,
                                     final int claimsHashCode,
                                     final int environmentHashCode) {
            return new StringBuilder(requestedUri).append(ifSpecified(ssoToken))
                                                  .append(ifSpecified(jwt))
                                                  .append(claimsHashCode != 0 ? "@" + claimsHashCode : "")
                                                  .append(environmentHashCode != 0 ? "@" + environmentHashCode : "")
                                                  .toString();
        }

        private static String ifSpecified(final String value) {
            if (value != null && !value.isEmpty()) {
                return "@" + value;
            }
            return "";
        }

        @VisibleForTesting
        static AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception>
        extractDurationFromTtl(final Duration defaultTimeout, final TimeService timeService) {
            //@Checkstyle:off
            return new AsyncFunction<Promise<ActionResponse, ResourceException>, Duration, Exception>() {

                @Override
                public Promise<Duration, Exception> apply(Promise<ActionResponse, ResourceException> promise)
                        throws Exception {
                    return promise.then(providedTtl(defaultTimeout), zeroTtl());
                }

                private Function<ActionResponse, Duration, Exception> providedTtl(final Duration defaultTimeout) {
                    return new Function<ActionResponse, Duration, Exception>() {
                        @Override
                        public Duration apply(ActionResponse response) throws Exception {
                            // The policy response is an array
                            JsonValue policyDecision = response.getJsonContent().get(0);
                            JsonValue advices = policyDecision.get("advices")
                                                              .defaultTo(object())
                                                              .expect(Map.class);
                            if (advices.size() > 0) {
                                // Do not cache policy decisions that contain any advices
                                return Duration.ZERO;
                            }
                            long ttl = policyDecision.get("ttl").defaultTo(Long.MAX_VALUE).asLong();
                            if (ttl == Long.MAX_VALUE) {
                                return defaultTimeout;
                            }
                            long difference = ttl - timeService.now();
                            if (difference <= 0) {
                                // No need to cache the policy decision as it is already over.
                                return Duration.ZERO;
                            }
                            return duration(difference, MILLISECONDS);
                        }
                    };
                }

                private Function<ResourceException, Duration, Exception> zeroTtl() {
                    return new Function<ResourceException, Duration, Exception>() {
                        @Override
                        public Duration apply(ResourceException e) throws Exception {
                            // Do not cache if case of Exception
                            return Duration.ZERO;
                        }
                    };
                }
            };
            //@Checkstyle:on
        }

        @Override
        public void close() throws IOException {
            cache.clear();
        }
    }

    /** Creates and initializes a policy enforcement filter in a heap environment. */
    public static class Heaplet extends GenericHeaplet {

        private CachePolicyDecisionFilter cacheFilter;

        @Override
        public Object create() throws HeapException {

            final String openamUrl = trailingSlash(config.get("openamUrl")
                                                         .as(evaluatedWithHeapProperties())
                                                         .required()
                                                         .asString());
            final String pepUsername = config.get("pepUsername")
                                             .required()
                                             .as(evaluatedWithHeapProperties())
                                             .asString();
            final String pepPassword = config.get("pepPassword")
                                             .required()
                                             .as(evaluatedWithHeapProperties())
                                             .asString();
            final String realm = config.get("realm").as(evaluatedWithHeapProperties()).defaultTo("/").asString();
            final String pepRealm = config.get("pepRealm")
                                          .as(evaluatedWithHeapProperties())
                                          .defaultTo(realm)
                                          .asString();
            Handler amHandler = config.get("amHandler")
                                      .defaultTo(FORGEROCK_CLIENT_HANDLER_HEAP_KEY)
                                      .as(requiredHeapObject(heap, Handler.class));
            final String ssoTokenHeader = config.get("ssoTokenHeader").as(evaluatedWithHeapProperties()).asString();

            try {
                final HeadlessAuthenticationFilter headlessAuthenticationFilter =
                        new HeadlessAuthenticationFilter(amHandler,
                                                         new URI(openamUrl),
                                                         pepRealm,
                                                         ssoTokenHeader,
                                                         pepUsername,
                                                         pepPassword);

                amHandler = chainOf(amHandler,
                                    headlessAuthenticationFilter,
                                    // /json/policies endpoint has a compatible 'evaluate' action between CREST
                                    // protocol v1 and v2 (response format unchanged)
                                    new ApiVersionProtocolHeaderFilter(PROTOCOL_VERSION_1));

                RequestHandler requestHandler = newRequestHandler(amHandler, normalizeToJsonEndpoint(openamUrl, realm));
                // Cache requested ?
                JsonValue cacheMaxExpiration = config.get("cacheMaxExpiration");
                if (cacheMaxExpiration.isNotNull()) {
                    String message = format("%s no longer uses a 'cacheMaxExpiration' configuration setting."
                                                    + "Use the following configuration to define a max expiration :%n"
                                                    + "\"cache\" {%n"
                                                    + "  \"enabled\": true%n"
                                                    + "  \"maxTimeout\": %s%n"
                                                    + "}%n",
                                            name,
                                            cacheMaxExpiration.asString());
                    logger.warn(message);
                }
                JsonValue cacheConfig = config.get("cache").as(evaluatedWithHeapProperties());
                boolean enabled = cacheConfig.get("enabled").defaultTo(false).asBoolean();
                if (cacheConfig.isNotNull() && enabled) {
                    final Duration defaultTimeout = cacheConfig.get("defaultTimeout")
                                                               .defaultTo("1 minute")
                                                               .as(duration());
                    final Duration maxTimeout = cacheConfig.get("maxTimeout")
                                                           .as(duration());
                    if (maxTimeout != null) {
                        if (maxTimeout.isUnlimited() || maxTimeout.isZero()) {
                            throw new HeapException("The max timeout value cannot be set to 'unlimited' or 'zero'");
                        }
                    }
                    ScheduledExecutorService executor = config.get("executor")
                                                              .defaultTo(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY)
                                                              .as(requiredHeapObject(heap,
                                                                                     ScheduledExecutorService.class));
                    TimeService timeService = heap.get(TIME_SERVICE_HEAP_KEY, TimeService.class);
                    cacheFilter = new CachePolicyDecisionFilter(executor,
                                                                timeService,
                                                                defaultTimeout,
                                                                maxTimeout);
                    requestHandler = new FilterChain(requestHandler, cacheFilter);
                }

                Handler failureHandler = Handlers.FORBIDDEN;
                if (config.isDefined("failureHandler")) {
                    failureHandler = config.get("failureHandler").as(requiredHeapObject(heap, Handler.class));
                }

                final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(requestHandler, failureHandler);

                filter.setApplication(config.get("application").as(evaluatedWithHeapProperties()).asString());

                if (config.get("ssoTokenSubject").isNull()
                        && config.get("jwtSubject").isNull()
                        && config.get("claimsSubject").isNull()) {
                    throw new HeapException(SUBJECT_ERROR);
                }

                filter.setSsoTokenSubject(config.get("ssoTokenSubject").as(expression(String.class)));
                filter.setJwtSubject(config.get("jwtSubject").as(expression(String.class)));
                filter.setClaimsSubject(asFunction(config.get("claimsSubject"), Object.class, heap.getProperties()));

                filter.setEnvironment(environment(heap.getProperties()));

                return filter;
            } catch (URISyntaxException e) {
                throw new HeapException(e);
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Function<Bindings, Map<String, List<Object>>, ExpressionException> environment(Bindings heapBindings) {
            // Double cast to satisfy compiler error due to type erasure
            return asFunction(config.get("environment"), (Class<List<Object>>) (Class) List.class, heapBindings);
        }

        @VisibleForTesting
        static <T> Function<Bindings, Map<String, T>, ExpressionException>
        asFunction(final JsonValue node, final Class<T> expectedType, final Bindings initialBindings) {
            if (node.isNull()) {
                return null;
            } else if (node.isString()) {
                return new Function<Bindings, Map<String, T>, ExpressionException>() {

                    @SuppressWarnings("unchecked")
                    @Override
                    public Map<String, T> apply(Bindings bindings) throws ExpressionException {
                        return node.as(JsonValues.expression(Map.class, initialBindings)).eval(bindings);
                    }
                };
            } else if (node.isMap()) {
                return new Function<Bindings, Map<String, T>, ExpressionException>() {
                    // Avoid 'environment' entry's value to be null (cause an error in AM)
                    Function<JsonValue, JsonValue, JsonException> filterNullMapValues =
                            new Function<JsonValue, JsonValue, JsonException>() {

                                @Override
                                public JsonValue apply(JsonValue value) {
                                    if (!value.isMap()) {
                                        return value;
                                    }

                                    Map<String, Object> object = object();
                                    for (String key : value.keys()) {
                                        JsonValue entry = value.get(key);
                                        if (entry.isNotNull()) {
                                            object.put(key, entry.getObject());
                                        }
                                    }
                                    return new JsonValue(object, value.getPointer());
                                }
                            };

                    @Override
                    public Map<String, T> apply(Bindings bindings) throws ExpressionException {
                        return node.as(evaluated(bindings))
                                   .as(filterNullMapValues) // see OPENIG-1402 and AME-12483
                                   .asMap(expectedType);
                    }
                };
            } else {
                throw new JsonValueException(node, "Expecting a String or a Map");
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
            if (cacheFilter != null) {
                try {
                    cacheFilter.close();
                } catch (IOException e) {
                    logger.warn("An error occurred while close the cache filter.", e);
                }
            }
        }
    }
}
