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
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.http.protocol.Status.GATEWAY_TIMEOUT;
import static org.forgerock.http.protocol.Status.OK;
import static org.forgerock.http.protocol.Status.UNAUTHORIZED;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.heap.Keys.CLIENT_HANDLER_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.LOGSINK_HEAP_KEY;
import static org.forgerock.openig.heap.Keys.TEMPORARY_STORAGE_HEAP_KEY;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.DEFAULT_POLICY_KEY;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.createKeyCache;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.Heaplet.normalizeToJsonEndpoint;
import static org.forgerock.util.Options.defaultOptions;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Handler;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.el.ExpressionException;
import org.forgerock.openig.handler.ClientHandler;
import org.forgerock.openig.handler.Handlers;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PolicyEnforcementFilterTest {
    private static final URI BASE_URI = URI.create("http://www.example.com:8090/openam/json/");
    private static final String OPENAM_URI = "http://www.example.com:8090/openam/";
    private static final String JWT_TOKEN = "eyJhbG...";
    private static final String REQUESTED_URI = "http://www.example.com/";
    private static final String RESOURCE_CONTENT = "Access granted!";
    private static final String TOKEN = "ARrrg...42*";

    private AttributesContext attributesContext;
    private SessionContext sessionContext;
    private ThreadSafeCache<String, Promise<JsonValue, ResourceException>> cache;
    @SuppressWarnings("rawtypes")
    private Expression<Map> target;

    @Mock
    private Handler next;

    @Mock
    private Handler policiesHandler;

    @Mock
    private Logger logger;

    @Captor
    private ArgumentCaptor<Callable<Promise<JsonValue, ResourceException>>> captor;

    @BeforeMethod
    public void setUp() throws ExpressionException {
        initMocks(this);
        sessionContext = new SessionContext(new RootContext(), new SimpleMapSession());
        attributesContext = new AttributesContext(sessionContext);
        attributesContext.getAttributes().put("password", "hifalutin");
        attributesContext.getAttributes().put("ssoTokenSubject", TOKEN);
        cache = new ThreadSafeCache<>(Executors.newSingleThreadScheduledExecutor());
        target = Expression.valueOf("${attributes.policy}", Map.class);

        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(policyDecisionResponse()));
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
                    field("target", "${attributes.myPolicy}"))) } };
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
            /* Missing ssoTokenSubject OR jwtSubject. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"))) },
            /* Invalid realm. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("realm", "   >>invalid<<    "),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("application", "anotherApplication"))) },
            /* Invalid target. */
            { json(object(
                    field("openamUrl", OPENAM_URI),
                    field("pepUsername", "jackson"),
                    field("pepPassword", "password"),
                    field("target", 123),
                    field("jwtSubject", "${attributes.jwtSubject}"),
                    field("application", "anotherApplication"))) } };
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

    @DataProvider
    private Object[][] invalidParameters() throws ExpressionException {
        return new Object[][] {
            { null, Expression.valueOf("${attributes.policy}", Map.class), Handlers.NO_CONTENT },
            { URI.create(OPENAM_URI), Expression.valueOf("${attributes.policy}", Map.class), null },
            { URI.create(OPENAM_URI), null, Handlers.NO_CONTENT } };
    }

    @DataProvider
    private static Object[][] invalidCacheMaxExpiration() {
        return new Object[][] {
            { "0 seconds" },
            { "unlimited" } };
    }

    @DataProvider
    private static Object[][] givenAndExpectedKey() {
        return new Object[][] {
            { REQUESTED_URI, TOKEN, null, REQUESTED_URI + "@" + TOKEN },
            { REQUESTED_URI, TOKEN, JWT_TOKEN, REQUESTED_URI + "@" + TOKEN + "@" + JWT_TOKEN },
            { REQUESTED_URI, "", JWT_TOKEN, REQUESTED_URI + "@" + JWT_TOKEN },
            { REQUESTED_URI, TOKEN, "", REQUESTED_URI + "@" + TOKEN } };
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

    @Test(dataProvider = "invalidParameters", expectedExceptions = NullPointerException.class)
    public void shouldFailToCreatePolicyEnforcementFilter(final URI baseUri,
                                                          final Expression<Map> target,
                                                          final Handler policiesHandler)
            throws Exception {
        new PolicyEnforcementFilter(baseUri, target, policiesHandler);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldNotAuthorizeAccessWhenRequestedResourceNotInPolicyDecision() throws Exception {
        // Given
        final String resource = "http://example.com/user.1/edit";
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri(resource);

        final Response displayEmptyResourceResponse = new Response();
        displayEmptyResourceResponse.setStatus(Status.OK)
                                    .setEntity(emptyPolicyDecision(resource));

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(displayEmptyResourceResponse));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(UNAUTHORIZED);
        final Map<String, ?> policyExtraAttributes = (Map<String, ?>) attributesContext.getAttributes()
                                                                                       .get(DEFAULT_POLICY_KEY);
        assertThat((Map<String, Object>) policyExtraAttributes.get("attributes")).isEmpty();
        assertThat((Map<String, Object>) policyExtraAttributes.get("advices")).isEmpty();
    }

    @Test
    public void shouldAuthorizeAccessToRequestedResource() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://example.com/resource.jpg");

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        when(next.handle(any(Context.class), eq(request)))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
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
        request.setUri("http://example.com/resource.jpg");

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(UNAUTHORIZED);
        assertThatAttributesAndAdvicesAreStoredInAttributesContext();
    }

    @Test
    public void shouldFailDueToInvalidServerResponse() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://example.com/resource.jpg");

        final Response errorResponse = new Response();
        errorResponse.setStatus(GATEWAY_TIMEOUT);

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();
        filter.setLogger(logger);

        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(errorResponse));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(UNAUTHORIZED);
        assertThat(finalResponse.getEntity().getString()).isEmpty();
        verify(logger).debug(any(Exception.class));
    }

    @Test(dataProvider = "realms")
    public static void shouldSucceedToCreateBaseUri(final String realm) throws Exception {
        assertThat(normalizeToJsonEndpoint("http://www.example.com:8090/openam/", realm).toASCIIString())
            .endsWith("/")
            .containsSequence("http://www.example.com:8090/openam/json/",
                              realm != null ? realm.trim() : "");
    }

    @Test(dataProvider = "invalidCacheMaxExpiration", expectedExceptions = HeapException.class)
    public void shouldFailToUseCacheForRequestedResource(final String cacheMaxExpiration) throws Exception {
        buildPolicyEnforcementFilter(buildHeapletConfiguration(cacheMaxExpiration));
    }

    @Test
    public void shouldSucceedToUseCacheForRequestedResource() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("GET").setUri("http://example.com/resource.jpg");

        PolicyEnforcementFilter filter = new PolicyEnforcementFilter(URI.create(OPENAM_URI),
                                                                     target,
                                                                     policiesHandler,
                                                                     duration("3 minutes"));
        filter.setSsoTokenSubject(Expression.valueOf("${attributes.ssoTokenSubject}", String.class));

        ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        filter.setCache(new ThreadSafeCache<String, Promise<JsonValue, ResourceException>>(executorService));

        when(next.handle(any(Context.class), eq(request)))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When first call
        filter.filter(attributesContext,
                      request,
                      next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        verify(next).handle(attributesContext, request);
        verify(executorService).schedule(captor.capture(), anyLong(), any(TimeUnit.class));

        // When second call
        // The policies handler, which provides the policy response, is not called
        // as the cache entry has not been evicted yet
        filter.filter(attributesContext,
                      request,
                      next).get();

        verify(next, times(2)).handle(attributesContext, request);

        // Mimic cache expiration
        captor.getValue().call();

        // When third call: the policiesHandler must do another call to get the policy decision result.
        filter.filter(attributesContext,
                      request,
                      next).get();

        verify(policiesHandler, times(2)).handle(any(Context.class), any(Request.class));
        verify(next, times(3)).handle(attributesContext, request);
    }

    @Test(dataProvider = "givenAndExpectedKey")
    public static void shouldSucceedToCreateCacheKey(final String requestedUri,
                                                     final String ssoToken,
                                                     final String jwt,
                                                     final String expected) {
        assertThat(createKeyCache(ssoToken, jwt, requestedUri)).isEqualTo(expected);
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

    private static Response policyDecisionResponse() {
        final Response response = new Response();
        response.setStatus(OK);
        response.setEntity(policyDecision());
        return response;
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
                            field("resource", "http://example.com/resource.jpg"),
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
        final Response response = new Response();
        response.setStatus(OK);
        response.setEntity(RESOURCE_CONTENT);
        return response;
    }

    private PolicyEnforcementFilter buildPolicyEnforcementFilter() throws Exception {
        final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(BASE_URI, target, policiesHandler);
        Expression<String> subject = Expression.valueOf("${attributes.ssoTokenSubject}",
                                                        String.class);
        filter.setSsoTokenSubject(subject);
        filter.setCache(cache);
        return filter;
    }

    private static JsonValue buildHeapletConfiguration(final String givenMaxCacheExpiration) {
        return json(object(
                       field("openamUrl", OPENAM_URI),
                       field("pepUsername", "jackson"),
                       field("pepPassword", "password"),
                       field("ssoTokenSubject", "${attributes.ssoTokenSubject}"),
                       field("policiesHandler", "policiesHandler"),
                       field("application", "myApplication"),
                       field("cacheMaxExpiration", givenMaxCacheExpiration)));
    }

    private PolicyEnforcementFilter buildPolicyEnforcementFilter(final JsonValue config) throws Exception {
        final PolicyEnforcementFilter.Heaplet heaplet = new PolicyEnforcementFilter.Heaplet();
        return (PolicyEnforcementFilter) heaplet.create(Name.of("myPolicyFilter"),
                                                                config,
                                                                buildDefaultHeap());
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException {
            // Nothing to do.
        }
    }

    public HeapImpl buildDefaultHeap() throws Exception {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
        heap.put(CLIENT_HANDLER_HEAP_KEY, new ClientHandler(new HttpClientHandler(defaultOptions())));
        heap.put("policiesHandler", policiesHandler);
        return heap;
    }
}
