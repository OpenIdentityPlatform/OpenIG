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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.openig.ai.filter;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.openidentityplatform.openig.ai.filter.llm.LLMProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.openig.util.JsonValues.expression;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LLMProxyFilterTest {

    private static final String CHAT_BODY = "{"
            + "\"model\":\"gpt-5.2\","
            + "\"messages\":["
            + "  {\"role\":\"user\",\"content\":\"Hello, how are you?\"}"
            + "]}";

    private static final String LONG_CHAT_BODY = "{"
            + "\"model\":\"gpt-4o\","
            + "\"messages\":["
            + "  {\"role\":\"user\",\"content\":\""
            + "a".repeat(400)   // 400 chars ≈ 100 tokens
            + "\"}"
            + "]}";

    private Handler mockNextHandler;

    @BeforeMethod
    public void setUp() {
        mockNextHandler = mock(Handler.class);
        Response upstreamResponse = new Response(Status.OK);
        upstreamResponse.setEntity("{\"choices\":[]}");
        when(mockNextHandler.handle(any(), any()))
                .thenReturn(Promises.newResultPromise(upstreamResponse));
    }

    @Test
    public void testValidRequest() throws Exception {

        LLMProxyFilter filter = buildFilter(LLMProvider.OPENAI, false);
        Request request = chatRequest();
        Response response = filter.filter(new RootContext(), request, mockNextHandler).get();

        verify(mockNextHandler, times(1)).handle(any(), any());
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void shouldAddHeadersToResponse() throws Exception {
        LLMProxyFilter filter = buildFilter(LLMProvider.OPENAI, true);
        Response response = filter.filter(contextWithSubject("alice"), chatRequest(), mockNextHandler).get();

        assertThat(response.getHeaders().getFirst(LLMProxyFilter.HEADER_LLM_PROVIDER))
                .isEqualTo("OPENAI");

        assertThat(response.getHeaders().getFirst(LLMProxyFilter.HEADER_LLM_IDENTITY))
                .isEqualTo("alice");

        assertThat(response.getHeaders().getFirst(LLMProxyFilter.HEADER_RATE_LIMIT_REMAINING))
                .isNotNull();
    }

    @Test
    public void shouldNotAddRateLimitHeaderWhenRateLimitDisabled() throws Exception {
        LLMProxyFilter filter = buildFilter(LLMProvider.OPENAI, false);
        Response response = filter.filter(new RootContext(), chatRequest(), mockNextHandler).get();

        assertThat(response.getHeaders().getFirst(LLMProxyFilter.HEADER_RATE_LIMIT_REMAINING))
                .isNull();
    }

    @Test
    public void shouldRewriteUriToProviderBaseUrl() throws Exception {
        LLMProxyFilter filter = buildFilter(LLMProvider.OPENAI, false);
        Request request = chatRequest();
        request.setUri(new URI("http://openig.local/v1/chat/completions"));

        filter.filter(new RootContext(), request, mockNextHandler).get();

        verify(mockNextHandler).handle(any(), argThat(req ->
                req.getUri().toString().startsWith("https://api.openai.com/v1")));
    }

    @Test
    public void shouldInjectBearerTokenForOpenAI() throws Exception {
        LLMProxyFilter filter = buildFilter(LLMProvider.OPENAI, false);
        Request request = chatRequest();
        request.getHeaders().put("Authorization", "Bearer dummy-key");

        filter.filter(new RootContext(), request, mockNextHandler).get();

        verify(mockNextHandler).handle(any(), argThat(req -> {
            String auth = req.getHeaders().getFirst("Authorization");
            return auth != null && auth.equals("Bearer test-api-key");
        }));
    }

    @Test
    public void shouldInjectXApiKeyHeaderForAnthropic() throws Exception {
        LLMProxyFilter filter = buildFilter(LLMProvider.ANTHROPIC, false);

        filter.filter(new RootContext(), chatRequest(), mockNextHandler).get();

        verify(mockNextHandler).handle(any(), argThat(req -> {
            String xApiKey = req.getHeaders().getFirst("x-api-key");
            return "test-api-key".equals(xApiKey);
        }));
    }
    @Test
    public void shouldUseCustomBaseUrlOverProviderDefault() throws Exception {
        LLMProxyFilter filter = new LLMProxyFilter(
                LLMProvider.OPENAI_COMPATIBLE, "http://localhost:11434/v1",
                "ollama-key", json("${attributes.sub}").as(expression(String.class)), false, null);

        Request request = chatRequest();
        request.setUri(new URI("http://openig.local/v1/chat/completions"));

        filter.filter(new RootContext(), request, mockNextHandler).get();

        verify(mockNextHandler).handle(any(), argThat(req ->
                req.getUri().toString().startsWith("http://localhost:11434/v1")));
    }


    @Test
    public void shouldReturn429WhenTokenBucketExhausted() throws Exception {
        // Tiny bucket – 50 tokens, slow refill
        TokenRateLimiter limiter = new TokenRateLimiter(50, duration("1 hour"));
        LLMProxyFilter filter = new LLMProxyFilter(
                LLMProvider.OPENAI, "https://api.openai.com/v1", "key",
                json("${attributes.sub}").as(expression(String.class)), true, limiter);

        // Drain the bucket with a big request (LONG_CHAT_BODY ≈ 100 tokens)
        Request bigRequest = new Request();
        bigRequest.setMethod("POST");
        bigRequest.setUri(new URI("http://openig.local/v1/chat/completions"));
        bigRequest.setEntity(LONG_CHAT_BODY);

        Response response = filter.filter(new RootContext(), bigRequest, mockNextHandler).get();

        assertThat(response.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();

        verify(mockNextHandler, never()).handle(any(), any());
    }

    @Test
    public void shouldAllowRequestForDifferentIdentityWhenOneIsExhausted() throws Exception {
        TokenRateLimiter limiter = new TokenRateLimiter(500, duration("1 hour"));
        LLMProxyFilter filter = new LLMProxyFilter(LLMProvider.OPENAI,
                LLMProvider.OPENAI.getDefaultBaseUrl(),
                "test-api-key",
                json("${attributes.sub}").as(expression(String.class)),
                true,
                limiter);

        // Drain alice's bucket
        limiter.tryConsume("alice", 500);

        // Bob's request should still go through
        Response response = filter.filter(contextWithSubject("bob"), chatRequest(), mockNextHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
    }

    private LLMProxyFilter buildFilter(LLMProvider provider,
                                       boolean rateLimitEnabled) {
        TokenRateLimiter limiter = rateLimitEnabled
                ? new TokenRateLimiter(10000, duration("1 minute"))
                : null;
        return new LLMProxyFilter(provider,
                provider.getDefaultBaseUrl(),
                "test-api-key",
                json("${attributes.sub}").as(expression(String.class)),
                rateLimitEnabled,
                limiter);
    }

    private Request chatRequest() throws Exception {
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(new URI("http://openig.local/v1/chat/completions"));
        request.getHeaders().put("Content-Type", "application/json");
        request.setEntity(CHAT_BODY);
        return request;
    }

    private AttributesContext contextWithSubject(String sub) {
        AttributesContext ctx = new AttributesContext(new RootContext());
        ctx.getAttributes().put("sub", sub);
        return ctx;
    }
}