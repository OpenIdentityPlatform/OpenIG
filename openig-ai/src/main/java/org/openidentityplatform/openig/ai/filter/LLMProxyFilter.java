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
 * Copyright 2026 3A Systems, LLC.
 */

package org.openidentityplatform.openig.ai.filter;

import com.github.benmanes.caffeine.cache.Ticker;
import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.Expression.DeferredBindingException;
import org.forgerock.openig.el.Expression.ReadPolicy;
import org.forgerock.openig.el.Expression.ReadUse;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.Duration;
import org.openidentityplatform.openig.ai.filter.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValueFunctions.duration;
import static org.forgerock.openig.el.Bindings.bindings;


/**
 * Identity-aware LLM proxy filter.
 *
 * <ol>
 *   <li><strong>Provider normalization</strong> – rewrites the upstream URI and injects the
 *       correct authentication header for the configured {@link LLMProvider}.</li>
 *   <li><strong>Identity extraction</strong> – reads the caller's identity from a configurable
 *       request attribute (populated upstream by, e.g., {@code OAuth2ResourceServerFilter}).</li>
 *   <li><strong>Token-based rate limiting</strong> – estimates the prompt-token cost of each
 *       request and enforces per-identity limits via {@link TokenRateLimiter}.
 *       Returns {@code 429 Too Many Requests} (with an exact {@code Retry-After} header)
 *       when the bucket is exhausted.</li>
 *   <li><strong>Response enrichment</strong> – adds {@code X-LLM-*} observability headers.</li>
 * </ol>
 *
 *
 * <h2>Heap configuration</h2>
 * <pre>{@code
 * {
 *   "name": "OpenAIProxy",
 *   "type": "LLMProxyFilter",
 *   "config": {
 *     "provider"          : "OPENAI",
 *     "baseUrl"           : "https://api.openai.com/v1",  // optional
 *     "apiKey"            : "${system['llm.apiKey']}",
 *     "sub"               : "${attributes.sub}",          // optional, expression, default "anonymous"
 *     "rateLimitEnabled"  : true,                         // optional, default true
 *     "rate": {
 *       "numberOfTokens"    : 10000,    // tokens per window (burst capacity)
 *       "duration"          : "1 minute"
 *       "cleaningInterval"  : "5 minutes"  // optional, bucket eviction period
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Response headers</h2>
 * <ul>
 *   <li>{@code X-LLM-Provider} — provider enum name</li>
 *   <li>{@code X-LLM-Identity} — resolved identity key</li>
 *   <li>{@code X-RateLimit-Remaining} — tokens left after this request</li>
 * </ul>
 */
public class LLMProxyFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LLMProxyFilter.class);

    public static final String HEADER_LLM_PROVIDER        = "X-LLM-Provider";
    public static final String HEADER_LLM_IDENTITY        = "X-LLM-Identity";
    public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";

    static final long   DEFAULT_RATE_LIMIT_NUMBER_OF_TOKENS = 10_000L;
    static final String DEFAULT_RATE_LIMIT_DURATION     = "1 minute";
    static final String DEFAULT_CLEANING_INTERVAL       = "5 minutes";

    static final long CHARS_PER_TOKEN = 4L;

    private final LLMProvider provider;
    private final String baseUrl;
    private final String apiKey;

    private final Expression<String> sub;

    private final boolean rateLimitEnabled;

    private final TokenRateLimiter rateLimiter;

    public LLMProxyFilter(LLMProvider provider,
                          String baseUrl,
                          String apiKey,
                          Expression<String> sub,
                          boolean rateLimitEnabled,
                          TokenRateLimiter rateLimiter) {
        this.provider = provider;
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : provider.getDefaultBaseUrl();
        this.apiKey = apiKey;
        this.sub = sub;
        this.rateLimitEnabled = rateLimitEnabled;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
        String identity = getIdentity(context, request);

        if (rateLimitEnabled) {

            long tokenCost = estimateTokenCost(request);

            long waitNs = rateLimiter.tryConsume(identity, tokenCost);

            if (waitNs > 0) {
                logger.info("LLMProxyFilter: rate limit exceeded "
                        + "identity={} cost={} waitNs={}", identity, tokenCost, waitNs);
                return Promises.newResultPromise(
                        rateLimitedResponse(identity, waitNs));
            }
        }

        rewriteRequest(request);

        return next.handle(context, request)
                .thenOnResult(response -> enrichResponse(response, identity));
    }

    private String getIdentity(Context context, Request request) {
        String identity = sub.eval(bindings(context, request));
        if(identity == null || identity.isEmpty()) {
            identity = "anonymous";
        }
        return identity;
    }


    /**
     * Estimates prompt-token cost: sums {@code content} lengths across all
     * {@code messages} entries and divides by 4 (chars-per-token rule of thumb).
     * Falls back to 500 if the body is absent or unparseable.
     */

    long estimateTokenCost(Request request) {
        try {
            JsonValue jsonEntity;
            byte[] body;
            try {
                body = request.getEntity().getBytes();
                jsonEntity = json(request.getEntity().getJson());
            } catch (IOException e) {
                logger.debug("Error parsing JSON request body", e);
                throw e;
            }
            JsonValue messages = jsonEntity.get("messages");
            if (messages.isNull() || !messages.isList()) {
                return Math.max(1L, body.length / CHARS_PER_TOKEN);
            }
            long charCount = 0;
            for (Object msg : messages.asList()) {
                JsonValue content = json(msg).get("content");
                if (content.isString()) {
                    charCount += content.asString().length();
                } else if (content.isList()) {
                    for (Object block : content.asList()) {
                        JsonValue text = json(block).get("text");
                        if (text.isString()) charCount += text.asString().length();
                    }
                }
            }
            return Math.max(1L, charCount / CHARS_PER_TOKEN);
        } catch (Exception e) {
            logger.debug("LLMProxyFilter: token cost estimation failed — using 500", e);
            return 500L;
        }
    }


    /**
     * Rewrites the upstream request:
     * <ul>
     *   <li>Removes the original {@code Authorization} / {@code x-api-key} headers.</li>
     *   <li>Injects the provider's auth header with the configured API key.</li>
     *   <li>Rewrites the URI: replaces the scheme+host+port with the configured base URL,
     *       keeping the original path and query.</li>
     * </ul>
     */
    void rewriteRequest(Request request) {
        // set the auth header according to the LLM provider settings
        request.getHeaders().remove("Authorization");
        request.getHeaders().remove("x-api-key");
        request.getHeaders().put(provider.getAuthHeaderName(),
                provider.buildAuthHeaderValue(apiKey));

        // URI rewrite
        URI original = request.getUri().asURI();
        String query = original.getRawQuery();

        try {
            String newUriStr = baseUrl
                    + (query != null ? "?" + query : "");
            request.setUri(new URI(newUriStr));
        } catch (URISyntaxException e) {
            logger.warn("LLMProxyFilter: could not rewrite URI – leaving original", e);
        }
    }


    private void enrichResponse(Response response, String identity) {
        response.getHeaders().put(HEADER_LLM_PROVIDER, provider.name());
        response.getHeaders().put(HEADER_LLM_IDENTITY, identity);
        if (rateLimitEnabled) {
            response.getHeaders().put(HEADER_RATE_LIMIT_REMAINING,
                    String.valueOf(rateLimiter.availableTokens(identity)));
        }
    }

    /**
     * Builds a {@code 429 Too Many Requests} response.
     * {@code Retry-After} is the ceiling-seconds computed directly from the
     * nanosecond wait returned by {@link TokenRateLimiter#tryConsume}.
     */
    private Response rateLimitedResponse(String identity, long waitNs) {
        long retryAfterSec = TimeUnit.NANOSECONDS.toSeconds(waitNs);
        Response response = new Response(Status.TOO_MANY_REQUESTS);
        response.getHeaders().put("Content-Type", "application/json");
        response.getHeaders().put("Retry-After", String.valueOf(retryAfterSec));
        response.getHeaders().put(HEADER_LLM_PROVIDER, provider.name());
        response.getHeaders().put(HEADER_LLM_IDENTITY, identity);
        if (rateLimitEnabled) {
            response.getHeaders().put(HEADER_RATE_LIMIT_REMAINING, "0");
        }
        response.setEntity("{\"error\":{\"message\":\"Token rate limit exceeded. "
                + "Retry after " + retryAfterSec + " second(s).\","
                + "\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\"}}");
        return response;
    }

    public static class Heaplet extends GenericHeaplet {

        private static final Set<String> REQUEST_BINDINGS =
                Set.of("request", "context", "contexts", "attributes", "session");
        private static final BeanInfo REQUEST_API = requestApi();
        private static final Set<String> REQUEST_PROPERTIES = Arrays.stream(REQUEST_API.getPropertyDescriptors())
                .filter(property -> property.getReadMethod() != null)
                .map(property -> property.getName())
                .collect(toUnmodifiableSet());
        private static final Set<String> REQUEST_METHODS = Arrays.stream(REQUEST_API.getMethodDescriptors())
                .map(method -> method.getName())
                .collect(toUnmodifiableSet());

        @Override
        public Object create() throws HeapException {

            String providerStr = config.get("provider").as(evaluatedWithHeapProperties()).required().asString();
            LLMProvider provider;
            try {
                provider = LLMProvider.valueOf(providerStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new HeapException("Unknown LLM provider '" + providerStr
                        + "'. Valid values: OPENAI, ANTHROPIC, MISTRAL, AZURE_OPENAI, OPENAI_COMPATIBLE", e);
            }
            String apiKey = config.get("apiKey").as(evaluatedWithHeapProperties()).required().asString();

            String baseUrl = config.get("baseUrl").as(evaluatedWithHeapProperties())
                                   .defaultTo(provider.getDefaultBaseUrl()).asString();

            boolean rateLimitEnabled = config.get("rateLimitEnabled").as(evaluatedWithHeapProperties())
                                             .defaultTo(true).asBoolean();

            Expression<String> sub = subjectExpression(config.get("sub"));

            TokenRateLimiter rateLimiter = null;
            if (rateLimitEnabled) {
                JsonValue rate = config.get("rate").defaultTo(null);

                long numberOfTokens = DEFAULT_RATE_LIMIT_NUMBER_OF_TOKENS;

                Duration duration = Duration.duration(DEFAULT_RATE_LIMIT_DURATION);

                Duration cleaningInterval = Duration.duration(DEFAULT_CLEANING_INTERVAL);

                if (rate != null && !rate.isNull()) {
                    numberOfTokens = rate.get("numberOfTokens")
                            .defaultTo(DEFAULT_RATE_LIMIT_NUMBER_OF_TOKENS).asLong();
                    duration = rate.get("duration").defaultTo(DEFAULT_RATE_LIMIT_DURATION).as(duration());

                    cleaningInterval = rate.get("cleaningInterval").defaultTo(DEFAULT_CLEANING_INTERVAL).as(duration());
                }

                try {
                    rateLimiter = new TokenRateLimiter(numberOfTokens, duration,
                            Ticker.systemTicker(), cleaningInterval);
                } catch (IllegalArgumentException e) {
                    throw new HeapException("LLMProxyFilter: invalid rate configuration", e);
                }
            }

            return new LLMProxyFilter(provider, baseUrl, apiKey, sub, rateLimitEnabled, rateLimiter);
        }

        private Expression<String> subjectExpression(JsonValue subject) {
            Expression<Object> configured = subject.as(expression(Object.class));
            if (configured != null) {
                try {
                    Object value = configured.eval(bindings(), REQUEST_BINDINGS);
                    subject = new JsonValue(value, subject.getPointer());
                } catch (DeferredBindingException e) {
                    Expression<String> startupSubject = heapSubject(subject, configured);
                    if (startupSubject != null) {
                        return startupSubject;
                    }
                }
            }
            return subject.defaultTo("anonymous").as(expression(String.class));
        }

        private Expression<String> heapSubject(JsonValue subject, Expression<Object> configured) {
            Map<String, Object> heapProperties = heap.getProperties().asMap();
            Set<String> heapBindings = new HashSet<>(REQUEST_BINDINGS);
            heapBindings.retainAll(heapProperties.keySet());
            if (heapBindings.isEmpty()) {
                return null;
            }

            Set<String> deferred = new HashSet<>(REQUEST_BINDINGS);
            deferred.removeAll(heapBindings);
            HeapSubjectReads reads = new HeapSubjectReads(heapBindings, heapProperties.get("request"));
            try {
                Object value = configured.eval(bindings(), deferred, reads);
                if (!(value instanceof String)) {
                    return null;
                }
                String source = (String) value;
                Expression<String> startupSubject = new JsonValue(source, subject.getPointer())
                        .as(expression(String.class));
                boolean literal = startupSubject.isLiteralText();
                if (reads.usesRequestConfiguration() || reads.returnsRequestString(source, literal)
                        || (!literal && reads.returnsSharedTemplate(source))) {
                    return startupSubject;
                }
            } catch (DeferredBindingException | JsonValueException e) {
                // Keep the original expression for request-time evaluation.
            }
            return null;
        }

        private static BeanInfo requestApi() {
            try {
                return Introspector.getBeanInfo(Request.class);
            } catch (IntrospectionException e) {
                throw new IllegalStateException("Cannot inspect HTTP Request members", e);
            }
        }

        private static final class HeapSubjectReads implements ReadPolicy {
            private final Set<String> heapBindings;
            private final Object heapRequest;
            private ResultStrings resultStrings = new ResultStrings();
            private boolean requestConfigurationRead;
            private boolean sharedBindingRead;

            private HeapSubjectReads(Set<String> heapBindings, Object heapRequest) {
                this.heapBindings = heapBindings;
                this.heapRequest = heapRequest;
            }

            @Override
            public boolean canRead(Object base, Object member, boolean method, ReadUse use) {
                if (base == null) {
                    // A member lookup can use heap configuration. Testing the whole request needs the live request.
                    return !"request".equals(member) || use.isReceiver()
                            || (!use.isOperand() && heapRequest instanceof String);
                }
                if (base != heapRequest) {
                    return true;
                }
                return !(method ? REQUEST_METHODS : REQUEST_PROPERTIES).contains(member);
            }

            @Override
            public void onRead(Object base, Object member, Object value, ReadUse use) {
                if (base == null && heapBindings.contains(member)) {
                    if ("request".equals(member)) {
                        rememberResultString(resultStrings.request, value, use);
                    } else {
                        sharedBindingRead = true;
                        rememberSharedValue(value, use);
                    }
                } else if (base != null && base == heapRequest) {
                    if (base instanceof String) {
                        rememberResultString(resultStrings.request, value, use);
                    } else {
                        // A non-HTTP member belongs to the heap configuration, including optional map keys.
                        requestConfigurationRead = true;
                    }
                } else if (resultStrings.receivers.contains(base)) {
                    rememberSharedValue(value, use);
                }
            }

            private void rememberSharedValue(Object value, ReadUse use) {
                if (value != null && use == ReadUse.RECEIVER) {
                    resultStrings.receivers.add(value);
                }
                rememberResultString(resultStrings.shared, value, use);
            }

            @Override
            public Object evaluateCall(Supplier<Object> invocation) {
                ResultStrings parent = resultStrings;
                ResultStrings inputs = new ResultStrings();
                resultStrings = inputs;
                try {
                    Object value = invocation.get();
                    if (value instanceof String) {
                        // Carry forward only text that survived this call, before EL appends other components.
                        String result = (String) value;
                        inputs.request.stream().filter(text -> retains(result, text)).forEach(parent.request::add);
                        inputs.shared.stream().filter(text -> retains(result, text)).forEach(parent.shared::add);
                    }
                    return value;
                } finally {
                    resultStrings = parent;
                }
            }

            @Override
            public Object evaluateArgument(Supplier<Object> argument) {
                ResultStrings parent = resultStrings;
                resultStrings = new ResultStrings();
                parent.arguments.add(resultStrings);
                try {
                    return argument.get();
                } finally {
                    resultStrings = parent;
                }
            }

            @Override
            public void onInvocation(Object base, Object method, Object[] arguments) {
                int firstResultArgument = 0;
                if (base instanceof Map) {
                    if ("get".equals(method) || "containsKey".equals(method)) {
                        return;
                    }
                    if ("getOrDefault".equals(method) && arguments.length == 2) {
                        // Only the default can supply text, and only when the key is absent.
                        if (((Map<?, ?>) base).containsKey(arguments[0])) {
                            return;
                        }
                        firstResultArgument = 1;
                    }
                }
                resultStrings.arguments.stream().skip(firstResultArgument).forEach(argument -> {
                    resultStrings.request.addAll(argument.request);
                    resultStrings.shared.addAll(argument.shared);
                });
            }

            private static void rememberResultString(Set<String> strings, Object value, ReadUse use) {
                if (use == ReadUse.RESULT && value instanceof String) {
                    strings.add((String) value);
                }
            }

            private boolean usesRequestConfiguration() {
                return requestConfigurationRead && !sharedBindingRead;
            }

            private boolean returnsRequestString(String result, boolean literal) {
                return !sharedBindingRead && (resultStrings.request.stream()
                        .anyMatch(text -> result.equals(text) || result.equals(text.trim()))
                        || (!literal && containsTemplate(resultStrings.request, result)));
            }

            private boolean returnsSharedTemplate(String result) {
                // Runtime bindings such as attributes have no fixed set of member names.
                return containsTemplate(resultStrings.shared, result);
            }

            private static boolean containsTemplate(Set<String> strings, String result) {
                return strings.stream().map(String::trim)
                        .anyMatch(text -> result.contains(text) && isTemplate(text));
            }

            private static boolean retains(String result, String text) {
                return result.equals(text) || result.contains(text.trim());
            }

            private static boolean isTemplate(String text) {
                try {
                    return !Expression.valueOf(text, String.class).isLiteralText();
                } catch (ExpressionException e) {
                    return false;
                }
            }

            private static final class ResultStrings {
                private final Set<String> request = new HashSet<>();
                private final Set<String> shared = new HashSet<>();
                private final Set<Object> receivers = Collections.newSetFromMap(new IdentityHashMap<>());
                private final List<ResultStrings> arguments = new ArrayList<>();
            }
        }
    }
}
