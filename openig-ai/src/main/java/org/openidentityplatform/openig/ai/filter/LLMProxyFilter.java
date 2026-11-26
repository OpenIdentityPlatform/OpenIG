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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
                    Expression<String> template = heapSubjectTemplate(subject, configured);
                    if (template != null) {
                        return template;
                    }
                }
            }
            return subject.defaultTo("anonymous").as(expression(String.class));
        }

        private Expression<String> heapSubjectTemplate(JsonValue subject, Expression<Object> configured) {
            Set<String> heapBindings = new HashSet<>(REQUEST_BINDINGS);
            heapBindings.retainAll(heap.getProperties().asMap().keySet());
            if (heapBindings.isEmpty()) {
                return null;
            }

            Set<String> deferred = new HashSet<>(REQUEST_BINDINGS);
            deferred.removeAll(heapBindings);
            Set<String> stringsRead = new HashSet<>();
            try {
                // Legacy templates may use ordinary EL fallback rules with the configured values.
                Object value = configured.eval(bindings(), deferred, heapBindings, text -> {
                    stringsRead.add(text);
                    return text;
                });
                if (!(value instanceof String)) {
                    return null;
                }
                String source = (String) value;
                stringsRead.removeIf(text -> !isTemplate(text));
                if (!stringsRead.isEmpty()) {
                    Expression<String> template = new JsonValue(source, subject.getPointer()).as(expression(String.class));
                    if (!template.isLiteralText()
                            && selectsHeapTemplate(configured, deferred, heapBindings, stringsRead, source)) {
                        return template;
                    }
                }
            } catch (DeferredBindingException | JsonValueException e) {
                // The compatibility evaluation could not select a template.
            }
            return null;
        }

        private boolean selectsHeapTemplate(Expression<Object> configured, Set<String> deferred,
                                            Set<String> heapBindings, Set<String> templates, String source) {
            String marker = "\uE000";
            while (source.contains(marker)) {
                marker += "\uE001";
            }
            // Tag the observed templates to distinguish returned text from values read only by a guard.
            String tag = marker;
            Object tagged = configured.eval(bindings(), deferred, heapBindings,
                    text -> templates.contains(text)
                            ? text.replace("${", tag + "${").replace("#{", tag + "#{")
                            : text);
            return tagged instanceof String && ((String) tagged).contains(tag)
                    && ((String) tagged).replace(tag, "").equals(source);
        }

        private boolean isTemplate(String text) {
            try {
                return !json(text).as(expression(String.class)).isLiteralText();
            } catch (JsonValueException e) {
                return false;
            }
        }
    }
}
