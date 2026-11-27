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

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promises;
import org.openidentityplatform.openig.ai.filter.llm.LLMProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
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

    @DataProvider
    public Object[][] subjectConfigurations() {
        return new Object[][] {
            { "${system['openig.test.llm.subject']}", "" },
            { "${true ? system['openig.test.llm.subject'] : attributes.sub}", "" },
            { "\\${attributes.sub}", "" },
            { "${subjectTemplate}", "" },
            { "${request}", "" },
            { "${true ? request : attributes.sub}", "" },
            { "${tenant}-${request}", "example-" },
            { "${tenant}-${request.uri.path}", "example-/" },
            { "${tenant}-${request.getUri().path}", "example-/" },
            { "${tenant}-${attributes.sub}", "example-" },
            { "${tenant}-${empty attributes ? 'guest' : attributes.sub}", "example-" },
            { "${system['openig.test.llm.subject']}-${attributes.sub}", "${attributes.sub}-" },
        };
    }

    @Test(dataProvider = "subjectConfigurations")
    public void shouldKeepSeparateQuotasForConfiguredSubjects(String subject, String prefix) throws Exception {
        assertSubjectQuotas(subject, prefix, object(
                field("tenant", "example"),
                field("subjectTemplate", "\\${attributes.sub}"),
                field("request", "\\${attributes.sub}"),
                field("attributes", object())));
    }

    @Test
    public void shouldUseHeapTemplateWhenOptionalOverrideIsAbsent() throws Exception {
        assertSubjectQuotas("${empty request.override ? request.defaultSubject : request.override}", "",
                object(field("request", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldKeepTemplateTextLiteralWhenUsingRequestAttributes() throws Exception {
        assertSubjectQuotas("${system['openig.test.llm.subject']}-${attributes.sub}", "${attributes.sub}-",
                object(field("attributes", object(field("unusedTemplate", "${attributes.sub}")))));
    }

    @Test
    public void shouldKeepTemplateTextLiteralWhenAHeapTemplateIsOnlyReadByTheGuard() throws Exception {
        assertSubjectQuotas("${system['openig.test.llm.subject']}"
                        + "-${empty attributes.defaultSubject ? attributes.sub : 'fallback'}",
                "${attributes.sub}-",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUseTrimmedHeapTemplate() throws Exception {
        assertSubjectQuotas("${trim(attributes.defaultSubject)}", "",
                object(field("attributes", object(field("defaultSubject", " ${attributes.sub} ")))));
    }

    @Test
    public void shouldUseComposedHeapTemplate() throws Exception {
        assertSubjectQuotas("tenant-${attributes.defaultSubject}", "tenant-",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUseHeapTemplatePassedThroughArrayAndJoin() throws Exception {
        assertSubjectQuotas("${join(array(attributes.defaultSubject), '')}", "",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldKeepJvmTemplateLiteralWhenJoinDoesNotUseTheSeparator() throws Exception {
        assertSubjectQuotas("${join(array(system['openig.test.llm.subject']), attributes.defaultSubject)}"
                        + "-${attributes.sub}", "${attributes.sub}-",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}"),
                        field("sub", "configured")))));
    }

    @Test
    public void shouldKeepJvmTemplateLiteralWhenReplaceFindsNoMatch() throws Exception {
        assertSubjectQuotas("${system['openig.test.llm.subject'].replace('missing', attributes.defaultSubject)}"
                        + "-${attributes.sub}", "${tenant}-",
                object(field("tenant", "fixed"),
                        field("attributes", object(field("defaultSubject", "${tenant}"),
                                field("sub", "configured")))), "${tenant}", Map.of("defaultSubject", "unused"));
    }

    @Test
    public void shouldUseHeapTemplateInsertedByReplace() throws Exception {
        assertSubjectQuotas("${'prefix-value'.replace('value', attributes.defaultSubject)}", "prefix-",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUseHeapTemplateWhenJvmGuardReadsTheSameText() throws Exception {
        assertSubjectQuotas("${system['openig.test.llm.subject'].startsWith('$') ? attributes.defaultSubject : 'guest'}",
                "", object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUseTrimmedWholeRequestTemplate() throws Exception {
        assertSubjectQuotas("${trim(request)}", "",
                object(field("request", " \\${attributes.sub} ")));
    }

    @Test
    public void shouldUseWholeRequestTemplateSelectedByStringGuard() throws Exception {
        assertSubjectQuotas("${request.startsWith('$') ? request : 'guest'}", "",
                object(field("request", "\\${attributes.sub}")));
    }

    @DataProvider
    public Object[][] templateLookupArguments() {
        return new Object[][] {
            { "attributes.defaultSubject", "system['openig.test.llm.subject']" },
            { "attributes.defaultSubject.trim()", "system['openig.test.llm.subject'].trim()" },
        };
    }

    @Test(dataProvider = "templateLookupArguments")
    public void shouldKeepJvmTemplateLiteralWhenTheHeapTemplateIsOnlyALookupKey(String key, String defaultValue)
            throws Exception {
        assertSubjectQuotas("${subjects.getOrDefault(" + key + ", " + defaultValue + ")}-${attributes.sub}",
                "${attributes.sub}-",
                object(field("subjects", object()),
                        field("attributes", object(field("defaultSubject", "${attributes.sub}"),
                                field("sub", "configured")))));
    }

    @Test
    public void shouldKeepMatchedMapValueLiteralWhenTheHeapTemplateIsAnUnusedDefault() throws Exception {
        assertSubjectQuotas("${subjects.getOrDefault('known', attributes.defaultSubject)}-${attributes.sub}",
                "${attributes.sub}-", object(field("subjects", object(field("known", "${attributes.sub}"))),
                        field("attributes", object(field("defaultSubject", "${attributes.sub}"),
                                field("sub", "configured")))));
    }

    @Test
    public void shouldUseHeapTemplateWhenTheMapSelectsItsDefault() throws Exception {
        assertSubjectQuotas("${subjects.getOrDefault('missing', attributes.defaultSubject)}", "",
                object(field("subjects", object()),
                        field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldKeepJvmTemplateLiteralWhenTheHeapTemplateIsOnlyASearchArgument() throws Exception {
        assertSubjectQuotas("${indexOf('prefix', attributes.defaultSubject)}"
                        + "-${system['openig.test.llm.subject']}-${attributes.sub}", "-1-${attributes.sub}-",
                object(field("attributes", object(field("defaultSubject", "${attributes.sub}"),
                        field("sub", "configured")))));
    }

    @Test
    public void shouldKeepTrimmedJvmTemplateLiteralWhenHeapTextIsOnlyAStringGuard() throws Exception {
        assertSubjectQuotas("${(empty attributes.defaultSubject || attributes.defaultSubject.startsWith('$')"
                        + " ? system['openig.test.llm.subject'] : 'guest').trim()}-${attributes.sub}",
                "${attributes.sub}-", object(field("attributes", object(field("defaultSubject", "${attributes.sub}"),
                        field("sub", "configured")))));
    }

    @Test
    public void shouldSelectHeapTemplateThroughStringGuard() throws Exception {
        assertSubjectQuotas("${request.defaultSubject.startsWith('$') ? request.defaultSubject : 'fallback'}", "",
                object(field("request", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUsePlainSubjectFromHeap() throws Exception {
        LLMProxyFilter filter = subjectFilter("${request.defaultSubject}",
                object(field("request", object(field("defaultSubject", "service-account")))));

        assertThat(acceptedIdentity(filter, "alice", Map.of())).isEqualTo("service-account");
    }

    @Test
    public void shouldSelectJvmTemplateThroughHeapCondition() throws Exception {
        assertSubjectQuotas("${request.mode == 'per-user' ? system['openig.test.llm.subject'] : 'fallback'}", "",
                object(field("request", object(field("mode", "per-user")))));
    }

    @Test
    public void shouldSelectJvmTemplateWhenOptionalHeapMemberIsMissing() throws Exception {
        assertSubjectQuotas("${empty request.override ? system['openig.test.llm.subject'] : request.override}", "",
                object(field("request", object())));
    }

    @Test
    public void shouldEvaluateRequestGuardWithAnEmptyHeapString() throws Exception {
        assertSubjectQuotas("${empty request ? 'guest' : request.uri.path}", "/",
                object(field("request", "")));
    }

    @DataProvider
    public Object[][] liveRequestSubjects() {
        return new Object[][] {
            { "attributes.sub", "" },
            { "request.uri.path", "/" },
        };
    }

    @Test(dataProvider = "liveRequestSubjects")
    public void shouldEvaluateWholeRequestGuardAfterAnOptionalMemberRead(String liveSubject, String prefix)
            throws Exception {
        assertSubjectQuotas("${empty request.override && empty request ? 'guest' : " + liveSubject + "}", prefix,
                object(field("request", object())));
    }

    @Test
    public void shouldPreserveRuntimeGuardWhenJvmTemplateMatchesHeapText() throws Exception {
        assertSubjectQuotas("${empty attributes.defaultSubject ? request.uri.path : system['openig.test.llm.subject']}",
                "/", object(field("attributes", object(field("defaultSubject", "${attributes.sub}")))));
    }

    @Test
    public void shouldUseRuntimeUriWhenHeapHasTheSameMember() throws Exception {
        assertSubjectQuotas("${request.uri.path}", "/",
                object(field("request", object(field("uri", object(field("path", "/configured")))))));
    }

    private void assertSubjectQuotas(String subject, String prefix, Map<String, Object> properties) throws Exception {
        assertSubjectQuotas(subject, prefix, properties, "${attributes.sub}", Map.of());
    }

    private void assertSubjectQuotas(String subject, String prefix, Map<String, Object> properties, String jvmTemplate,
            Map<String, Object> requestAttributes) throws Exception {
        String property = "openig.test.llm.subject";
        String previous = System.setProperty(property, jvmTemplate);
        try {
            LLMProxyFilter filter = subjectFilter(subject, properties);

            assertThat(acceptedIdentity(filter, "alice", requestAttributes)).isEqualTo(prefix + "alice");
            assertThat(acceptedIdentity(filter, "bob", requestAttributes)).isEqualTo(prefix + "bob");
            Response exhausted = filter.filter(contextWithSubject("alice", requestAttributes),
                    chatRequest("/alice"), mockNextHandler).get();
            assertThat(exhausted.getStatus()).isEqualTo(Status.TOO_MANY_REQUESTS);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private LLMProxyFilter subjectFilter(String subject, Map<String, Object> properties) throws Exception {
        Name name = Name.of("llmProxy");
        HeapImpl heap = new HeapImpl(name);
        heap.init(json(object(field("properties", properties))));
        return (LLMProxyFilter) new LLMProxyFilter.Heaplet().create(name,
                json(object(
                        field("provider", "OPENAI"),
                        field("apiKey", "test-api-key"),
                        field("sub", subject),
                        field("rate", object(field("numberOfTokens", 4), field("duration", "1 hour"))))), heap);
    }

    private String acceptedIdentity(LLMProxyFilter filter, String subject, Map<String, Object> requestAttributes)
            throws Exception {
        Response response = filter.filter(contextWithSubject(subject, requestAttributes),
                chatRequest("/" + subject), mockNextHandler).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        return response.getHeaders().getFirst(LLMProxyFilter.HEADER_LLM_IDENTITY);
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
        return chatRequest("/v1/chat/completions");
    }

    private Request chatRequest(String path) throws Exception {
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(new URI("http://openig.local" + path));
        request.getHeaders().put("Content-Type", "application/json");
        request.setEntity(CHAT_BODY);
        return request;
    }

    private AttributesContext contextWithSubject(String sub) {
        return contextWithSubject(sub, Map.of());
    }

    private AttributesContext contextWithSubject(String sub, Map<String, Object> attributes) {
        AttributesContext ctx = new AttributesContext(new RootContext());
        ctx.getAttributes().putAll(attributes);
        ctx.getAttributes().put("sub", sub);
        return ctx;
    }
}
