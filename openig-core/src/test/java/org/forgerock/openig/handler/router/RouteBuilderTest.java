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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.openig.handler.router;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.http.MutableUri.uri;
import static org.forgerock.http.protocol.Response.newResponsePromise;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openig.handler.router.Files.getTestResourceDirectory;
import static org.forgerock.openig.handler.router.Files.getTestResourceFile;
import static org.forgerock.openig.handler.router.MonitoringResourceProvider.DEFAULT_PERCENTILES;
import static org.forgerock.openig.heap.HeapUtilsTest.buildDefaultHeap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.routing.Router;
import org.forgerock.http.session.Session;
import org.forgerock.http.session.SessionContext;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.filter.ResponseHandler;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.EndpointRegistry;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class RouteBuilderTest {

    private HeapImpl heap;

    @Mock
    private Session session;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        heap = buildDefaultHeap();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        DestroyDetectHandler.destroyed = false;
    }

    @DataProvider
    public static Object[][] invalidRouteConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
            /** This route throws a HeapException */
            { json(object(field("name", "invalid"),
                          field("heap", array(
                              object(
                                  field("name", "Detection"),
                                  field("type", "org.forgerock.openig.handler.router.DestroyDetectHandler")),
                              object(
                                  field("name", "invalidStaticRequestFilter"),
                                  field("type", "StaticRequestFilter"),
                                  field("config", object(
                                      field("method", "POST"),
                                      field("uri", "http://www.example.com:8081"),
                                      field("form", object()),
                                      field("entity", "Mutually exclusive, throws the HeapException")))))),
                          field("handler", "myHandler"))) },
            /** This route throws a JsonValueException */
            { json(object(field("name", "invalid"),
                          field("heap", array(
                              object(
                                  field("name", "Detection"),
                                  field("type", "org.forgerock.openig.handler.router.DestroyDetectHandler")),
                              object(
                                  field("name", "invalidStaticRequestFilter"),
                                  field("type", "StaticRequestFilter"),
                                  field("config", object(
                                      field("method", "POST"),
                                      field("uri", "http://www.example.com:8081"),
                                      field("form", array())))))),
                          field("handler", "myHandler"))) } };
        // @Checkstyle:on
    }

    @Test(description = "OPENIG-329")
    public void testHandlerCanBeInlinedWithNoHeap() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("inlined-handler-route.json"));

        Context context = new RootContext();

        assertThat(route.handle(context, new Request())
                        .get()
                        .getStatus()).isEqualTo(Status.TEAPOT);
    }

    private RouteBuilder newRouteBuilder(Router router) {
        return new RouteBuilder(heap, Name.of("anonymous"), new EndpointRegistry(router, ""));
    }

    private RouteBuilder newRouteBuilder() {
        return newRouteBuilder(new Router());
    }

    @Test
    public void testUnnamedRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("route.json"));
        assertThat(route.getName()).isEqualTo("route");
    }

    @Test(expectedExceptions = JsonValueException.class)
    public void testMissingHandlerRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        builder.build(getTestResourceFile("missing-handler-route.json"));
    }

    @Test
    public void testConditionalRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("conditional-route.json"));
        route.start();

        AttributesContext context = new AttributesContext(new RootContext());
        context.getAttributes().put("value", 42);
        assertThat(route.accept(context, null)).isTrue();
        context.getAttributes().put("value", 44);
        assertThat(route.accept(context, null)).isFalse();
    }

    @Test
    public void testNamedRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("named-route.json"));
        route.start();
        assertThat(route.getName()).isEqualTo("my-route");
    }

    @Test(dataProvider = "invalidRouteConfigurations")
    public void shouldDestroyHeapRouteWhenItFailsToLoadIt(final JsonValue routeConfiguration) throws Exception {
        Router router = new Router();
        RouteBuilder builder = newRouteBuilder(router);

        try {
            builder.build(routeConfiguration, Name.of("invalidRoute"), "default");
            fail("Must throw an exception");
        } catch (HeapException | RuntimeException ex) {
            // Ensure that the route's heap we tried to load was destroyed
            assertThat(DestroyDetectHandler.destroyed).isTrue();
            // Ensure that the generated route endpoint is unregistered as well
            Response response = router.handle(new RootContext(), new Request().setUri("/invalid")).get();
            assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        }
    }

    @Test
    public void testRouteDestroy() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(new File(getTestResourceDirectory("routes"), "destroy-test.json"));
        route.start();

        assertThat(DestroyDetectHandler.destroyed).isFalse();
        route.destroy();
        assertThat(DestroyDetectHandler.destroyed).isTrue();
    }

    @Test
    public void testRebaseUriRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("rebase-uri-route.json"));
        route.start();

        Context context = new RootContext();
        Request request = new Request();
        request.setUri("http://openig.forgerock.org/demo");

        route.handle(context, request);

        assertThat(request.getUri()).isEqualTo(uri("https://localhost:443/demo"));
    }

    @Test
    public void testSessionRouteLoading() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("session-route.json"));
        route.start();

        SimpleMapSession simpleSession = new SimpleMapSession();
        SessionContext sessionContext = new SessionContext(new RootContext(), simpleSession);
        Request request = new Request();


        assertThat(route.handle(sessionContext, request)
                        .get()
                        .getHeaders()
                        .getFirst("Set-Cookie")).isNotNull();
        assertThat(sessionContext.getSession()).isEmpty();

    }

    @Test
    public void testSessionIsReplacingTheSessionForDownStreamHandlers() throws Exception {
        RouteBuilder builder = newRouteBuilder();
        Route route = builder.build(getTestResourceFile("session-route.json"));
        route.start();

        SimpleMapSession simpleSession = new SimpleMapSession();
        simpleSession.put("foo", "bar");
        SessionContext sessionContext = new SessionContext(new RootContext(), simpleSession);

        route.handle(sessionContext, new Request());

        // session-route uses the inner class SessionHandler, that binds a value for the session's key "ForgeRock".
        // As this is not the same session, then the original session is not impacted.
        assertThat(simpleSession.get("ForgeRock")).isNull();
    }

    /**
     * The api-registration.json route configuration declares a RegisterRouteHandler instance.
     * This component is a simple handler that return OK, but it doesn't matter for this test.
     * Here we want to ensure that the additional Handler registered into routes/registration-test/ping endpoint
     * has been registered and is active
     */
    @Test
    public void testApiRegistration() throws Exception {

        Router router = new Router();
        RouteBuilder builder = newRouteBuilder(router);
        Route route = builder.build(getTestResourceFile("api-registration.json"));
        route.start();

        // Ensure that api endpoint is working
        Request request = new Request().setUri("/registration-test/objects/register/ping");
        Response response = router.handle(new RootContext(), request).get();
        assertThat(response.getEntity().getString()).isEqualTo("Pong");

        // Ensure that the path is right
        assertThat(route.handle(null, null).get().getEntity().getString())
                .isEqualTo("/registration-test/objects/register/ping");

        // Ensure that api endpoint is not accessible anymore after route has been destroyed
        route.destroy();
        Response notFound = router.handle(new RootContext(), request).get();
        assertThat(notFound.getStatus()).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    public void testMonitoringIsEnabled() throws Exception {
        Router router = new Router();
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"), new EndpointRegistry(router, ""));
        Route route = builder.build(getTestResourceFile("monitored-route.json"));
        route.start();

        Request request = new Request().setMethod("GET").setUri("/monitored/monitoring");
        Response response = router.handle(new AttributesContext(new RootContext()), request).get();

        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getHeaders().get(ContentTypeHeader.class).getType()).isEqualTo("application/json");
        assertThat(response.getEntity().getJson()).isInstanceOf(Map.class);
    }

    @Test
    public void testMonitoringIsDisabledByDefault() throws Exception {
        Router router = new Router();
        RouteBuilder builder = new RouteBuilder(heap, Name.of("anonymous"), new EndpointRegistry(router, ""));
        Route route = builder.build(getTestResourceFile("not-monitored-route.json"));
        route.start();

        Request request = new Request().setMethod("GET").setUri("/not-monitored-route/monitoring");
        Response response = router.handle(new AttributesContext(new RootContext()), request).get();

        assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
    }

    @DataProvider
    public static Object[][] monitoredRouteConfigs() {
        // @Checkstyle:off
        return new Object[][] {
                // config, expectations: enabled, percentiles
                { json(object(field("handler", "Forwarder"))), false, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", true))), true, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", false))), false, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", "${true}"))), true, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", object(field("enabled", true))))), true, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", object(field("enabled", false))))), false, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", object(field("enabled", "${true}"))))), true, DEFAULT_PERCENTILES },
                { json(object(field("handler", "Forwarder"),
                              field("monitor", object(field("enabled", true),
                                                      field("percentiles", array(0.1)))))),
                  true,
                  singletonList(0.1d) },
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "monitoredRouteConfigs")
    public void testMonitoringConfiguration(final JsonValue config,
                                            final boolean enabled,
                                            final List<Double> percentiles) throws Exception {
        //Given
        Router router = new Router();
        heap.put("Forwarder", new ResponseHandler(Status.ACCEPTED));
        RouteBuilder builder = newRouteBuilder(router);
        Route route = builder.build(config, Name.of("route"), "route");
        route.start();

        // When
        Request request = new Request().setMethod("GET").setUri("/route/monitoring");
        Response response = router.handle(new AttributesContext(new RootContext()), request).get();

        // Then
        if (enabled) {
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            JsonValue data = json(response.getEntity().getJson());
            JsonValue percentilesValues = data.get(new JsonPointer("responseTime/percentiles"));
            for (Double percentile : percentiles) {
                assertThat(percentilesValues.get(String.valueOf(percentile)).isNumber()).isTrue();
            }
        } else {
            assertThat(response.getStatus()).isEqualTo(Status.NOT_FOUND);
        }
    }

    private static class SimpleMapSession extends HashMap<String, Object> implements Session {
        private static final long serialVersionUID = 1L;

        @Override
        public void save(Response response) throws IOException { }
    }

    public static class SessionHandler implements Handler {

        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            Session session = context.asContext(SessionContext.class).getSession();
            session.put("ForgeRock", "OpenIG");
            return Promises.newResultPromise(new Response());
        }

        public static class Heaplet extends GenericHeaplet {

            @Override
            public Object create() throws HeapException {
                return new SessionHandler();
            }
        }
    }

    public static class RegisterRouteHandler implements Handler {

        private final String path;

        public RegisterRouteHandler(String path) {
            this.path = path;
        }

        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            return newResponsePromise(new Response(Status.OK).setEntity(path));
        }

        public static class Heaplet extends GenericHeaplet {

            private EndpointRegistry.Registration registration;

            @Override
            public Object create() throws HeapException {
                EndpointRegistry registry = endpointRegistry();
                registration = registry.register("ping", new Handler() {
                    @Override
                    public Promise<Response, NeverThrowsException> handle(final Context context,
                                                                          final Request request) {
                        return newResponsePromise(new Response(Status.OK).setEntity("Pong"));
                    }
                });
                return new RegisterRouteHandler(registration.getPath());
            }

            @Override
            public void destroy() {
                registration.unregister();
            }
        }
    }

}
