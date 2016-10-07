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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.openig.openam;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.io.IO.newTemporaryStorage;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.FORBIDDEN;
import static org.forgerock.http.protocol.Status.GATEWAY_TIMEOUT;
import static org.forgerock.http.protocol.Status.INTERNAL_SERVER_ERROR;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.ResourceException.newResourceException;
import static org.forgerock.json.resource.Responses.newActionResponse;
import static org.forgerock.openig.el.Bindings.bindings;
import static org.forgerock.openig.heap.Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.CachePolicyDecisionFilter.createKeyCache;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.DEFAULT_POLICY_KEY;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.Heaplet.asFunction;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.Heaplet.normalizeToJsonEndpoint;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.FilterChain;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.el.LeftValueExpression;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.Function;
import org.forgerock.util.PerItemEvictionStrategyCache;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PolicyEnforcementFilterTest {
    private static final JsonValue CLAIMS_SUBJECT = json(object(field("iss", "jwt-bearer-client"),
                                                                field("sub", "client_id"),
                                                                field("aud", "http://example.com:8088/openam"),
                                                                field("exp", 1300819380)));
    private static final List<String> IP_LIST = singletonList("192.168.1.1");
    private static final JsonValue ENVIRONMENT = json(object(field("IP", IP_LIST)));
    private static final String OPENAM_URI = "http://www.example.com:8090/openam/";
    private static final String JWT_TOKEN = "eyJhbG...";
    private static final String REQUESTED_URI = "http://www.example.com/";
    private static final String RESOURCE_CONTENT = "Access granted!";
    private static final String RESOURCE_URI = "http://example.com/resource.jpg";
    private static final String TOKEN = "ARrrg...42*";

    private AttributesContext attributesContext;
    private Request resourceRequest;
    @SuppressWarnings("rawtypes")
    private LeftValueExpression<Map> target;

    @Mock
    private Handler next;

    @Mock
    private RequestHandler requestHandler;

    @Captor
    private ArgumentCaptor<Runnable> captor;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        attributesContext = new AttributesContext(new RootContext());
        attributesContext.getAttributes().put("password", "hifalutin");
        attributesContext.getAttributes().put("ssoTokenSubject", TOKEN);
        attributesContext.getAttributes().put("client_id", "OpenIG");
        Bindings bindings = bindings(attributesContext, null);
        @SuppressWarnings("rawtypes")
        final LeftValueExpression<Map> claimsSubject = LeftValueExpression.valueOf("${attributes.claimsSubject}",
                                                                                   Map.class);
        claimsSubject.set(bindings, singletonMap("iss", "jwt-bearer-client"));
        @SuppressWarnings("rawtypes")
        final LeftValueExpression<Map> environmentMap = LeftValueExpression.valueOf("${attributes.environmentMap}",
                                                                                    Map.class);
        environmentMap.set(bindings, singletonMap("IP", IP_LIST));
        target = LeftValueExpression.valueOf("${attributes.policy}", Map.class);

        when(requestHandler.handleAction(any(Context.class), any(ActionRequest.class)))
                .thenReturn(newActionResponse(json(policyDecision())).asPromise());

        resourceRequest = new Request().setMethod("GET").setUri(RESOURCE_URI);
    }

    @DataProvider
    private static Object[][] validConfigurations() {
        return new Object[][] {
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                    field("application", "myApplication"))) },
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("application", "anotherApplication"))) },
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("target", "${attributes.myPolicy}"))) },
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("claimsSubject", CLAIMS_SUBJECT.getObject()))) },
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("claimsSubject", "${attributes.claimsSubject}"))) },
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                    field("environment", ENVIRONMENT.getObject()))) } };
    }

    @DataProvider
    private static Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing URL to OpenAM. */
            { json(object(
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"))) },
            /* Missing pepUsername. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepPassword", "password"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"))) },
            /* Missing pepPassword. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"))) },
            /* Invalid pepRealm. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("pepRealm", 42),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                    field("application", "myApplication"))) },
            /* Missing ssoTokenSubject OR jwtSubject OR claimsSubject. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"))) },
            /* Invalid target. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("target", 123),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("application", "anotherApplication"))) },
            /* Invalid claims. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("claimsSubject", 123),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("application", "anotherApplication"))) },
            /* Invalid environment */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                    field("environment", 123))) } };
    }

    @Test(dataProvider = "invalidConfigurations",
          expectedExceptions = { JsonValueException.class, HeapException.class })
    public void shouldFailToCreateHeaplet(final JsonValue invalidConfiguration) throws Exception {
        buildPolicyEnforcementFilter(invalidConfiguration);
    }

    @Test(dataProvider = "validConfigurations")
    public void shouldSucceedToCreateHeaplet(final JsonValue config) throws Exception {
        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(config);
        assertThat(filter).isNotNull();
    }

    @DataProvider
    private static Object[][] invalidParameters() throws ExpressionException {
        return new Object[][] {
            { LeftValueExpression.valueOf("${attributes.policy}", Map.class), null },
            { null, mock(RequestHandler.class) } };
    }

    @SuppressWarnings("rawtypes")
    @Test(dataProvider = "invalidParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailToCreatePolicyEnforcementFilter(final LeftValueExpression<Map> target,
                                                          final RequestHandler requestHandler)
            throws Exception {
        new PolicyEnforcementFilter(target, requestHandler);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldNotAuthorizeAccessWhenRequestedResourceNotInPolicyDecision() throws Exception {
        final String resource = "http://example.com/user.1/edit";

        // Given
        when(requestHandler.handleAction(any(Context.class), any(ActionRequest.class)))
                .thenReturn(newActionResponse(json(emptyPolicyDecision(resource))).asPromise());

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        // When
        final Request request = new Request().setMethod("GET").setUri(resource);
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));
        assertThat(finalResponse.getStatus()).isEqualTo(FORBIDDEN);
        final Map<String, ?> policyExtraAttributes = (Map<String, ?>) attributesContext.getAttributes()
                                                                                       .get(DEFAULT_POLICY_KEY);
        assertThat((Map<String, Object>) policyExtraAttributes.get("attributes")).isEmpty();
        assertThat((Map<String, Object>) policyExtraAttributes.get("advices")).isEmpty();
    }

    @Test
    public void shouldAuthorizeAccessToRequestedResource() throws Exception {
        // Given
        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        when(next.handle(any(Context.class), eq(resourceRequest)))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     resourceRequest,
                                                     next).get();
        // Then
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));
        verify(next).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(OK);
        assertThat(finalResponse.getEntity().getString()).isEqualTo(RESOURCE_CONTENT);
        assertThatAttributesAndAdvicesAreStoredInAttributesContext();
    }

    @Test
    public void shouldNotAuthorizeAccessToRequestedResource() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("POST"); // The POST action is denied by policy decision.
        request.setUri(RESOURCE_URI);

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));
        assertThat(finalResponse.getStatus()).isEqualTo(FORBIDDEN);
        assertThatAttributesAndAdvicesAreStoredInAttributesContext();
    }

    @Test
    public void shouldFailDueToInvalidServerResponse() throws Exception {
        // Given
        when(requestHandler.handleAction(any(Context.class), any(ActionRequest.class)))
                .thenReturn(newResourceException(GATEWAY_TIMEOUT.getCode(), "").<ActionResponse>asPromise());

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     resourceRequest,
                                                     next).get();

        // Then
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));
        assertThat(finalResponse.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(finalResponse.getEntity().getString()).isEmpty();
    }

    @DataProvider
    private static Object[][] realms() {
        return new Object[][] {
            { null },
            { "" },
            { "/" },
            { "realm/" },
            { "/realm" },
            { "/realm/" },
            { "/realm " },
            { " realm/" } };
    }

    @Test(dataProvider = "realms")
    public static void shouldSucceedToCreateBaseUri(final String realm) throws Exception {
        assertThat(normalizeToJsonEndpoint("http://www.example.com:8090/openam/", realm).toASCIIString())
            .endsWith("/")
            .containsSequence("http://www.example.com:8090/openam/json/",
                              realm != null ? realm.trim() : "");
    }

    @DataProvider
    private static Object[][] invalidCacheMaxExpiration() {
        return new Object[][] {
            { "0 seconds" },
            { "unlimited" } };
    }

    @Test(dataProvider = "invalidCacheMaxExpiration", expectedExceptions = HeapException.class)
    public void shouldFailToUseCacheForRequestedResource(final String cacheMaxExpiration) throws Exception {
        buildPolicyEnforcementFilter(buildHeapletConfiguration(cacheMaxExpiration));
    }

    @Test
    public void shouldSucceedToUseCacheForRequestedResource() throws Exception {
        // Given
        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        PerItemEvictionStrategyCache<String, Promise<ActionResponse, ResourceException>> cache =
                new PerItemEvictionStrategyCache<>(executorService, duration(1, TimeUnit.MINUTES));
        RequestHandler chainedHandler = new FilterChain(requestHandler,
                                                        new PolicyEnforcementFilter.CachePolicyDecisionFilter(cache));
        PolicyEnforcementFilter filter = new PolicyEnforcementFilter(target, chainedHandler);
        filter.setSsoTokenSubject(Expression.valueOf("${attributes.ssoTokenSubject}", String.class));

        when(next.handle(any(Context.class), eq(resourceRequest)))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When first call
        filter.filter(attributesContext,
                      resourceRequest,
                      next).get();
        // Then
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));
        verify(next).handle(attributesContext, resourceRequest);
        verify(executorService).schedule(captor.capture(), anyLong(), any(TimeUnit.class));

        // When second call
        // The policies handler, which provides the policy response, is not called
        // as the cache entry has not been evicted yet
        filter.filter(attributesContext,
                      resourceRequest,
                      next).get();

        verify(next, times(2)).handle(attributesContext, resourceRequest);
        // No other call to the requestHandler has to be done here
        verify(requestHandler).handleAction(any(Context.class), any(ActionRequest.class));

        // Mimic cache expiration
        captor.getValue().run();

        // When third call: the requestHandler must do another call to get the policy decision result.
        filter.filter(attributesContext,
                      resourceRequest,
                      next).get();

        verify(requestHandler, times(2)).handleAction(any(Context.class), any(ActionRequest.class));
        verify(next, times(3)).handle(attributesContext, resourceRequest);
    }

    @DataProvider
    private static Object[][] givenAndExpectedKey() {
        return new Object[][] {
            { REQUESTED_URI, TOKEN, null, 0, 0, REQUESTED_URI + "@" + TOKEN },
            { REQUESTED_URI, TOKEN, JWT_TOKEN, 0, 0, REQUESTED_URI + "@" + TOKEN + "@" + JWT_TOKEN },
            { REQUESTED_URI, "", JWT_TOKEN, 0, 0, REQUESTED_URI + "@" + JWT_TOKEN },
            { REQUESTED_URI, TOKEN, "", 0, 0, REQUESTED_URI + "@" + TOKEN },
            { REQUESTED_URI, TOKEN, null, CLAIMS_SUBJECT.asMap().hashCode(), ENVIRONMENT.asMap().hashCode(),
              REQUESTED_URI + "@" + TOKEN + "@" + CLAIMS_SUBJECT.asMap().hashCode() + "@"
                      + ENVIRONMENT.asMap().hashCode() } };
    }

    @Test(dataProvider = "givenAndExpectedKey")
    public void shouldSucceedToCreateCacheKey(final String requestedUri,
                                              final String ssoToken,
                                              final String jwt,
                                              final int claimsHashCode,
                                              final int environmentHashCode,
                                              final String expected) {
        assertThat(createKeyCache(requestedUri, ssoToken, jwt, claimsHashCode, environmentHashCode))
                .isEqualTo(expected);
    }

    @DataProvider
    private static Object[][] invalidClaimsEnvironment() {
        return new Object[][] {
            { null, json(object(field("IP", "Not an array"))) },
            { null, json(object(field("IP", 123))) },
            { field("This is", "Not map"), null } };
    }

    @Test(dataProvider = "invalidClaimsEnvironment", expectedExceptions = { JsonValueException.class,
                                                                            ExpressionException.class })
    public void shouldFailToBuildResource(final Object claims, final Object environment) throws Exception {
        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(
                buildMinimalHeapletConfiguration().put("claimsSubject", claims)
                                                  .put("environment", environment));

        filter.buildResources(attributesContext, resourceRequest);
    }

    @Test
    public void shouldSucceedToBuildResource() throws Exception {
        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(buildMinimalHeapletConfiguration());

        final JsonValue resources = filter.buildResources(attributesContext, resourceRequest);

        assertThat(resources).hasSize(2);
        assertResourcesContainResourceURIAndSsoToken(resources);
    }

    @DataProvider
    private static Object[][] claims() {
        return new Object[][] {
            { json(object(field("iss", "jwt-bearer-client"),
                          field("sub", "${attributes.client_id}"),
                          field("subs", "${false}"),
                          field("aud", "http://example.com:8088/openam"),
                          field("exp", 1300819380))),
              json(object(field("iss", "jwt-bearer-client"),
                      field("sub", "OpenIG"),
                      field("subs", false),
                      field("aud", "http://example.com:8088/openam"),
                      field("exp", 1300819380L))).asMap() },
            { "${attributes.claimsSubject}", json(object(field("iss", "jwt-bearer-client"))).asMap() } };
    }

    @Test(dataProvider = "claims")
    public void shouldSucceedToBuildClaimsResource(final Object claims,
                                                   final Map<String, Object> evaluated) throws Exception {

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(
                buildMinimalHeapletConfiguration().put("claimsSubject", claims));

        final JsonValue resources = filter.buildResources(attributesContext, resourceRequest);

        assertThat(resources).hasSize(2);
        assertResourcesContainResourceURIAndSubjects(resources, "ssoToken", "claims");
        assertThat(resources.get("subject").get("claims").asMap()).isEqualTo(evaluated);
    }

    @DataProvider
    private static Object[][] environment() {
        return new Object[][] {
            { json(object(field("IP", IP_LIST))) },
            { "${attributes.environmentMap}" } };
    }

    @Test(dataProvider = "environment")
    public void shouldSucceedToBuildEnvironmentResource(final Object environment) throws Exception {

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(
                buildMinimalHeapletConfiguration().put("environment", environment));

        final JsonValue resources = filter.buildResources(attributesContext, resourceRequest);

        assertThat(resources).hasSize(3);
        assertResourcesContainResourceURIAndSsoToken(resources);
        assertThat(resources.get("environment").get("IP").asList()).isEqualTo(IP_LIST);
    }

    @DataProvider
    private static Object[][] application() {
        return new Object[][] {
            { "custom-application" },
            { "${env['PATH']}" } };
    }

    @Test(dataProvider = "application")
    public void shouldSucceedToBuildApplicationResource(final String value) throws Exception {

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter(
                buildMinimalHeapletConfiguration().put("application", value));

        final JsonValue resources = filter.buildResources(attributesContext, resourceRequest);

        assertThat(resources).hasSize(3);
        assertResourcesContainResourceURIAndSsoToken(resources);
        assertThat(resources.get("application").asString()).isNotEmpty();
    }

    @Test
    public void shouldReturnNullWhenJsonValueInputIsNull() throws Exception {
        Function<Bindings, Map<String, List>, ExpressionException> function =
                asFunction(json(null), List.class, bindings());
        assertThat(function).isNull();
    }

    @DataProvider
    public static Object[][] asFunctionProvider() {
        //@Checkstyle:off
        return new Object[][] {
                { json("${foo}"), object(field("bar", array(1, 2, 3))) },
                { json(object(field("quix", "${foo['bar']}"))), object(field("quix", array(1, 2, 3)))}
        };
        //@Checkstyle:on
    }

    @Test(dataProvider = "asFunctionProvider")
    public void shouldEvaluateToMap(JsonValue input, Map<String, Object> expectedOutput) throws Exception {
        Bindings bindings = bindings().bind("foo", object(field("bar", array(1, 2, 3))));

        Function<Bindings, Map<String, List>, ExpressionException> function =
                asFunction(input, List.class, bindings());

        assertThat(function.apply(bindings)).isEqualTo(expectedOutput);
    }

    private static void assertResourcesContainResourceURIAndSsoToken(final JsonValue resources) {
        assertResourcesContainResourceURIAndSubjects(resources, "ssoToken");
    }

    private static void assertResourcesContainResourceURIAndSubjects(final JsonValue resources,
                                                                     final String... subjects) {
        assertThat(resources.get("resources").get(0).asString()).isEqualTo(RESOURCE_URI);
        Map<String, Object> subjectsResources = resources.get("subject").asMap();
        assertThat(subjectsResources).containsOnlyKeys(subjects);
        if (subjectsResources.containsKey("ssoToken")) {
            // The ssoToken is given in the buildMinimalHeapletConfiguration
            assertThat(resources.get("subject").get("ssoToken").asString()).isEqualTo(TOKEN);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertThatAttributesAndAdvicesAreStoredInAttributesContext() {
        final Map<String, ?> policyExtraAttributes = (Map<String, ?>) attributesContext.getAttributes()
                                                                                       .get(DEFAULT_POLICY_KEY);
        assertThat(policyExtraAttributes).containsOnlyKeys("attributes", "advices");
        assertThat((Map<String, List<String>>) policyExtraAttributes.get("attributes"))
            .containsOnlyKeys("customAttribute").containsEntry("customAttribute",
                                                               singletonList("myCustomAttribute"));

        assertThat((Map<String, List<String>>) policyExtraAttributes.get("advices"))
            .containsOnlyKeys("AuthLevelConditionAdvice").containsEntry("AuthLevelConditionAdvice",
                                                                        singletonList("3"));
    }

    /**
     * An example of a response given by OpenAM when the resource is supported
     * by the policy:
     * <pre>
     * {@code
     * [{
     *      "advices": {
     *          "AuthLevelConditionAdvice" : [ "3" ]
     *      }
     *      "ttl": 9223372036854775807,
     *      "resource": "http://example.com/resource.jpg",
     *      "actions": {
     *          "POST": false,
     *          "GET": true
     *      },
     *      "attributes": {
     *          "customAttribute": [ "myCustomAttribute" ]
     *      }
     * }]
     * }</pre>
     */
    private static Object policyDecision() {
        return array(object(field("advices", object(field("AuthLevelConditionAdvice", array("3")))),
                            field("ttl", Long.MAX_VALUE),
                            field("resource", RESOURCE_URI),
                            field("actions", object(field("POST", false), field("GET", true))),
                            field("attributes", object(field("customAttribute", array("myCustomAttribute"))))));
    }

    /**
     * An example of a response given by OpenAM when the resource is not supported by the policy.
     * <pre>
     * {@code
     * [{
     *      "advices": {},
     *      "ttl": 9223372036854775807,
     *      "resource": "http://localhost:8082/url-not-in-policy",
     *      "actions": {},
     *      "attributes": {}
     * }]
     * }</pre>
     */
    private static Object emptyPolicyDecision(final String resource) {
        return array(object(field("advices", object()),
                            field("ttl", Long.MAX_VALUE),
                            field("resource", resource),
                            field("actions", object()),
                            field("attributes", object())));
    }

    private static Response displayResourceResponse() {
        final Response response = new Response(OK);
        response.setEntity(RESOURCE_CONTENT);
        return response;
    }

    private PolicyEnforcementFilter buildPolicyEnforcementFilter() throws Exception {
        final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(target, requestHandler);
        Expression<String> subject = Expression.valueOf("${attributes.ssoTokenSubject}",
                                                        String.class);
        filter.setSsoTokenSubject(subject);
        return filter;
    }

    private static JsonValue buildMinimalHeapletConfiguration() {
        return json(object(
                       field("openamUrl", OPENAM_URI),
                       field("pepUsername", "jackson"),
                       field("pepPassword", "password"),
                       field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                       field("amHandler", "amHandler")));
    }

    private static JsonValue buildHeapletConfiguration(final String givenMaxCacheExpiration) {
        return buildMinimalHeapletConfiguration().put("application", "myApplication")
                                                 .put("cacheMaxExpiration", givenMaxCacheExpiration);
    }

    private PolicyEnforcementFilter buildPolicyEnforcementFilter(final JsonValue config) throws Exception {
        final PolicyEnforcementFilter.Heaplet heaplet = new PolicyEnforcementFilter.Heaplet();
        return (PolicyEnforcementFilter) heaplet.create(Name.of("myPolicyFilter"),
                                                                config,
                                                                buildDefaultHeap());
    }

    public HeapImpl buildDefaultHeap() {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, newTemporaryStorage());
        heap.put(SCHEDULED_EXECUTOR_SERVICE_HEAP_KEY, newSingleThreadScheduledExecutor());
        heap.put(FORGEROCK_CLIENT_HANDLER_HEAP_KEY, mock(Handler.class));
        heap.put("amHandler", mock(Handler.class));
        return heap;
    }
}
