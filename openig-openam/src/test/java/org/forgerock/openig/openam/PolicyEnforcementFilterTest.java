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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.openig.openam;

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
import static org.forgerock.openig.openam.PolicyEnforcementFilter.createKeyCache;
import static org.forgerock.openig.openam.PolicyEnforcementFilter.Heaplet.normalizeToJsonEndpoint;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.Executors;

import org.forgerock.http.Handler;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.ClientHandler;
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

    @Mock
    private Handler next;

    @Mock
    private Handler policiesHandler;

    @Mock
    private Logger logger;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        sessionContext = new SessionContext(new RootContext(), new SimpleMapSession());
        attributesContext = new AttributesContext(sessionContext);
        attributesContext.getAttributes().put("password", "hifalutin");
        attributesContext.getAttributes().put("ssoTokenSubject", TOKEN);
        cache = new ThreadSafeCache<>(Executors.newSingleThreadScheduledExecutor());
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
                    field("application", "anotherApplication"))) } };
    }

    @DataProvider
    private static Object[][] invalidConfigurations() {
        return new Object[][] {
            /* Missing url to OpenAM. */
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
            /* Invalid realm */
            { json(object(
                          field("openamUrl", OPENAM_URI),
                          field("pepUsername", "jackson"),
                          field("pepPassword", "password"),
                          field("realm", "   >>invalid<<    "),
                          field("jwtSubject", "${attributes.jwtSubject}"),
                          field("application", "anotherApplication"))) } };
    }

    @DataProvider
    private static Object[][] realms() throws Exception {
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
    private Object[][] invalidParameters() throws Exception {
        return new Object[][] {
            { null, policiesHandler },
            { URI.create(OPENAM_URI), null } };
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
    public void shouldFailToCreatePolicyEnforcementFilter(final URI baseUri, final Handler policiesHandler)
            throws Exception {
        new PolicyEnforcementFilter(baseUri, policiesHandler);
    }

    @Test
    public void shouldFailToGetAccessToRequestedResource() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://example.com/user.1/edit");

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        sessionContext.getSession().put("SSOToken", TOKEN);
        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(policyDecisionAsJsonResponse()));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     policiesHandler).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    public void shouldSucceedToGetAccessToRequestedResource() throws Exception {
        // Given
        final Request request = new Request();
        request.setMethod("GET");
        request.setUri("http://example.com/resource.jpg");

        final PolicyEnforcementFilter filter = buildPolicyEnforcementFilter();

        sessionContext.getSession().put("SSOToken", TOKEN);
        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(policyDecisionAsJsonResponse()))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     policiesHandler).get();
        // Then
        verify(policiesHandler, times(2)).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(OK);
        assertThat(finalResponse.getEntity().getString()).isEqualTo(RESOURCE_CONTENT);
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

        sessionContext.getSession().put("SSOToken", TOKEN);
        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(errorResponse));

        // When
        final Response finalResponse = filter.filter(attributesContext,
                                                     request,
                                                     policiesHandler).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        assertThat(finalResponse.getStatus()).isEqualTo(UNAUTHORIZED);
        assertThat(finalResponse.getEntity().getString()).isEmpty();
        verify(logger).debug(any(Exception.class));
    }

    @Test(dataProvider = "realms")
    public void shouldSucceedToCreateBaseUri(final String realm) throws Exception {
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
        sessionContext.getSession().put("SSOToken", TOKEN);
        final Request request = new Request();
        request.setMethod("GET").setUri("http://example.com/resource.jpg");
        final PolicyEnforcementFilter filter =
                buildPolicyEnforcementFilter(buildHeapletConfiguration("50 milliseconds"));

        when(policiesHandler.handle(any(Context.class), any(Request.class)))
            .thenReturn(newResponsePromise(policyDecisionAsJsonResponse()));

        when(next.handle(attributesContext, request))
            .thenReturn(newResponsePromise(displayResourceResponse()));

        // When first call
        filter.filter(attributesContext,
                      request,
                      next).get();
        // Then
        verify(policiesHandler).handle(any(Context.class), any(Request.class));
        verify(next).handle(attributesContext, request);

        // When second call
        // The policies handler, which provides the policy response, is not called
        // as the policy decision has been saved into cache for 50 ms. (cacheMaxExpiration)
        filter.filter(attributesContext,
                      request,
                      next).get();

        verify(next, times(2)).handle(attributesContext, request);

        Thread.sleep(60); // Sleep until we exceed the cache timeout.
        // (As the ttl (Long.MAX_VALUE > cacheMaxExpiration, the cache must use
        // the cacheMaxExpiration timeout. The previous cached policy must have been removed.

        // When third call: the policiesHandler must do another call to get the policy decision result.
        filter.filter(attributesContext,
                      request,
                      next).get();

        verify(policiesHandler, times(2)).handle(any(Context.class), any(Request.class));
        verify(next, times(3)).handle(attributesContext, request);
    }

    @Test(dataProvider = "givenAndExpectedKey")
    public void shouldSucceedToCreateCacheKey(final String requestedUri,
                                              final String ssoToken,
                                              final String jwt,
                                              final String expected) {
        assertThat(createKeyCache(ssoToken, jwt, requestedUri)).isEqualTo(expected);
    }

    private static Response policyDecisionAsJsonResponse() {
        final Response response = new Response();
        response.setStatus(OK);
        response.setEntity(getPolicyDecision());
        return response;
    }

    private static Object getPolicyDecision() {
        return array(object(field("advices", object()),
                            field("ttl", Long.MAX_VALUE),
                            field("resource", "http://example.com/resource.jpg"),
                            field("actions", object(field("POST", false), field("GET", true))),
                            field("attributes", object())));
    }

    private static Response displayResourceResponse() {
        final Response response = new Response();
        response.setStatus(OK);
        response.setEntity(RESOURCE_CONTENT);
        return response;
    }

    private PolicyEnforcementFilter buildPolicyEnforcementFilter() throws Exception {
        final PolicyEnforcementFilter filter = new PolicyEnforcementFilter(BASE_URI, policiesHandler);
        Expression<String> subject = Expression.valueOf("${attributes.ssoTokenSubject}",
                                                        String.class);
        filter.setSsoTokenSubject(subject);
        filter.setCache(cache);
        return filter;
    }

    private JsonValue buildHeapletConfiguration(final String givenMaxCacheExpiration) {
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
        return (PolicyEnforcementFilter) heaplet.create(Name.of("myAssignmentFilter"),
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
